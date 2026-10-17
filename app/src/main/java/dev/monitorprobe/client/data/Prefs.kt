package dev.monitorprobe.client.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Everything the app remembers between launches: the hub's address, the admin
 * session cookie, and the refresh interval.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("monitor", Context.MODE_PRIVATE)

    /** The hub's origin, e.g. `https://monitor.example.com`. Empty until set. */
    var baseUrl: String
        get() = sp.getString(KEY_BASE_URL, "").orEmpty()
        set(value) = sp.edit().putString(KEY_BASE_URL, value).apply()

    /** Seconds between refreshes while the app is in the foreground. */
    var intervalSeconds: Int
        get() = sp.getInt(KEY_INTERVAL, 5)
        set(value) = sp.edit().putInt(KEY_INTERVAL, value).apply()

    /** Whether a session cookie is on file, i.e. whether a login may still be live. */
    val hasSession: Boolean get() = sp.contains(KEY_COOKIE)

    // --- the session cookie, stored whole so it survives a restart ---

    fun saveCookie(serialized: String) = sp.edit().putString(KEY_COOKIE, serialized).apply()

    fun loadCookie(): String? = sp.getString(KEY_COOKIE, null)

    fun clearCookie() = sp.edit().remove(KEY_COOKIE).apply()

    /** The widget's last successful frame, so it can draw before the first refresh. */
    var widgetCache: String
        get() = sp.getString(KEY_WIDGET_CACHE, "").orEmpty()
        set(value) = sp.edit().putString(KEY_WIDGET_CACHE, value).apply()

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_INTERVAL = "interval_seconds"
        const val KEY_COOKIE = "session_cookie"
        const val KEY_WIDGET_CACHE = "widget_cache"
    }
}

/**
 * Normalizes what a user typed into an origin the hub's paths can hang off:
 * adds `https://` when no scheme was given, keeps an explicit `http://` (a hub
 * on a LAN is a supported deployment), and drops any path, query or trailing slash.
 */
fun normalizeBaseUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""
    val withScheme = when {
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        else -> "https://$trimmed"
    }
    return withScheme.trimEnd('/').let { url ->
        // Cut anything from the first slash after the authority.
        val schemeEnd = url.indexOf("://") + 3
        val pathStart = url.indexOf('/', schemeEnd)
        if (pathStart > 0) url.substring(0, pathStart) else url
    }
}
