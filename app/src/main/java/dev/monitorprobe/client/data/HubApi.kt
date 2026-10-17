package dev.monitorprobe.client.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** A hub answered with something that is not a success. The message is the hub's own line of text. */
class HubException(val status: Int, message: String) : Exception(message)

/**
 * Talks to a monitor hub. Everything is relative to the configured origin, which
 * is what the panel does too: the hub serves its API under `/api` on the same host.
 */
class HubApi(private val prefs: Prefs) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(PersistentCookieJar(prefs))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun origin(): HttpUrl? = prefs.baseUrl.toHttpUrlOrNull()

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val origin = origin() ?: throw HubException(0, "还没有填写主控地址")
        val request = Request.Builder().url(origin.resolve(path) ?: throw HubException(0, "地址无效")).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // The hub writes one plain-text line for the reader; anything else
                // came from a proxy in front of it and is described by its status.
                throw HubException(response.code, body.ifBlank { "主控返回 HTTP ${response.code}" })
            }
            body
        }
    }

    suspend fun nodes(): LiveFrame = json.decodeFromString(get("/api/nodes"))

    suspend fun versions(): Versions = json.decodeFromString(get("/api/version"))

    suspend fun dbStats(): DbStats = json.decodeFromString(get("/api/db"))

    suspend fun settings(): Settings = json.decodeFromString(get("/api/settings"))

    suspend fun settingsRaw(): JsonObject = json.decodeFromString(get("/api/settings"))

    /**
     * One node's history. `series` is deliberately omitted: the hub then answers
     * both the resource samples and the probe latencies from a single scan of the
     * window, where asking for `series=metrics` and then `series=ping` would scan
     * it twice. `hours` is clamped by the hub to its own retention window.
     */
    suspend fun history(nodeId: Long, hours: Int = 6, points: Int = 180): History {
        val body = get("/api/nodes/$nodeId/metrics?hours=$hours&points=$points")
        return json.decodeFromString(body)
    }

    /** Exchanges the admin password for a session cookie, which the jar then carries. */
    suspend fun login(password: String): Boolean = withContext(Dispatchers.IO) {
        val origin = origin() ?: throw HubException(0, "还没有填写主控地址")
        val payload = json.encodeToString(LoginBody.serializer(), LoginBody(password))
        val request = Request.Builder()
            .url(origin.resolve("/api/auth/login")!!)
            .post(payload.toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HubException(response.code, body.ifBlank { "登录失败（HTTP ${response.code}）" })
            true
        }
    }

    suspend fun logout() {
        val origin = origin() ?: return
        runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(origin.resolve("/api/auth/logout")!!).post("".toRequestBody(jsonMedia)).build()
                client.newCall(request).execute().close()
            }
        }
        prefs.clearCookie()
    }

    /** Clears anything the client holds; used when the address changes. */
    fun reset() {
        prefs.clearCookie()
    }
}

/** The login body the hub's `LoginBody` deserializes. */
@Serializable
private data class LoginBody(val password: String)

/**
 * Keeps the hub's session cookie across launches. The hub sets it `HttpOnly`, which
 * only concerns browsers; OkHttp's jar is the client's own store, so the app reads
 * it back the same way it would in a browser tab.
 */
private class PersistentCookieJar(private val prefs: Prefs) : CookieJar {

    private val lock = Any()
    private val cookies = linkedMapOf<String, Cookie>()

    init {
        prefs.loadCookie()?.let { stored ->
            stored.split('\u0000').forEach { entry ->
                val parts = entry.split('\u0001')
                if (parts.size == 5) {
                    val host = parts[0]
                    cookies[host] = Cookie.Builder()
                        .name(parts[1])
                        .value(parts[2])
                        .path("/")
                        .hostOnlyDomain(host)
                        .apply { parts[4].toLongOrNull()?.let { expiresAt(it) } }
                        .build()
                }
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, list: List<Cookie>) {
        // The hub answers most requests without a Set-Cookie, and rewriting the
        // store for an empty list meant a SharedPreferences write - and an fsync -
        // on every single poll.
        if (list.isEmpty()) return
        synchronized(lock) {
            var changed = false
            for (cookie in list) {
                val previous = cookies[cookie.domain]
                if (previous?.value != cookie.value || previous.expiresAt != cookie.expiresAt) {
                    cookies[cookie.domain] = cookie
                    changed = true
                }
            }
            if (changed) persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            return cookies.values.filter { it.appliesTo(url.host) && it.expiresAt > now }
        }
    }

    private fun persist() {
        val serialized = cookies.values.joinToString("\u0000") { c ->
            listOf(c.domain, c.name, c.value, c.path, c.expiresAt.toString()).joinToString("\u0001")
        }
        prefs.saveCookie(serialized)
    }

    /** A cookie whose domain is the host itself, or a parent of it. */
    private fun Cookie.appliesTo(host: String) =
        domain == host || host.endsWith(".$domain")
}
