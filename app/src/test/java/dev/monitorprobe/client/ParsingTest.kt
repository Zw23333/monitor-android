package dev.monitorprobe.client

import dev.monitorprobe.client.data.History
import dev.monitorprobe.client.data.LiveFrame
import dev.monitorprobe.client.data.Versions
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing tests built from payloads shaped exactly like the hub's own: the node
 * frame comes from `web-admin/src/lib/api.ts`'s `Node`/`Metrics`, and the history
 * from `db::close_bucket` / `db::metrics` in the hub's source. These are the shapes
 * most likely to drift, and a wrong `@SerialName` is silent at compile time.
 */
class ParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Test
    fun `node frame parses the hub's field names`() {
        val frame = json.decodeFromString(
            LiveFrame.serializer(),
            """
            {
              "admin": true,
              "nodes": [{
                "id": 7, "name": "东京-1", "public": true, "online": true,
                "last_seen_ago": 3,
                "os": "Debian GNU/Linux 12", "kernel": "6.1.0", "arch": "x86_64",
                "virt": "kvm", "cpu_name": "AMD EPYC", "cpu_cores": 4,
                "mem_total": 8589934592, "swap_total": 2147483648, "disk_total": 107374182400,
                "agent_version": "1.4.2", "country": "JP", "group": "日本",
                "price": 5.5, "currency": "USD", "billing_cycle": "monthly",
                "expires_at": "2027-01-01", "traffic_limit": 1099511627776,
                "traffic_mode": "sum", "traffic_reset_day": 1,
                "total_rx": 100, "total_tx": 200, "month_rx": 10, "month_tx": 20,
                "month_used": 30, "month_start": "2026-10-01",
                "metrics": {
                  "iface": "eth0", "uptime": 86400, "cpu": 12.5, "load": [0.1, 0.2, 0.3],
                  "mem_total": 8589934592, "mem_used": 2147483648,
                  "swap_total": 2147483648, "swap_used": 0,
                  "disk_total": 107374182400, "disk_used": 53687091200,
                  "net_rx": 1024, "net_tx": 2048, "total_rx": 100, "total_tx": 200,
                  "month_rx": 10, "month_tx": 20, "tcp": 42, "udp": 3, "procs": 120
                }
              }]
            }
            """.trimIndent(),
        )

        assertTrue(frame.admin)
        assertEquals(1, frame.nodes.size)
        val node = frame.nodes.first()
        assertEquals("东京-1", node.name)
        assertEquals(3L, node.lastSeenAgo)
        assertEquals("日本", node.group)
        assertEquals("JP", node.country)
        assertEquals(1099511627776L, node.trafficLimit)
        assertEquals(30L, node.monthUsed)
        assertEquals("2027-01-01", node.expiresAt)
        assertEquals("1.4.2", node.agentVersion)

        val metrics = requireNotNull(node.metrics)
        assertEquals("eth0", metrics.iface)
        assertEquals(12.5, metrics.cpu, 0.0001)
        assertEquals(3, metrics.load.size)
        assertEquals(0.25f, metrics.memFraction, 0.0001f)
        assertEquals(0.5f, metrics.diskFraction, 0.0001f)
        assertEquals(1024L, metrics.netRx)
        assertEquals(42L, metrics.tcp)
    }

    @Test
    fun `history parses both series and the ping row shape`() {
        val history = json.decodeFromString(
            History.serializer(),
            """
            {
              "step": 60,
              "probes": {"1": "上海电信", "2": "联通"},
              "loss": {"1": 0.14},
              "metrics": [{
                "ts": 1760000000, "cpu": 33.3, "mem_used": 1000, "disk_used": 2000,
                "net_rx": 300, "net_tx": 400, "net_rx_max": 500, "net_tx_max": 600,
                "cpu_max": 50.0, "minutes": 1, "swap_used": 0,
                "tcp": 10, "udp": 2, "procs": 90
              }],
              "ping": [
                {"task_id": 1, "ts": 1760000000, "latency": 37, "band": [31, 52], "loss": 2},
                {"task_id": 1, "ts": 1760000060},
                {"task_id": 2, "ts": 1760000000, "latency": 88}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(60L, history.step)
        assertEquals(2, history.probes.size)
        assertEquals("上海电信", history.probeName("1"))
        assertEquals(0.14, history.probeLoss("1"), 0.0001)
        assertEquals(0.0, history.probeLoss("2"), 0.0001)
        assertEquals(listOf("1", "2"), history.probeIds)

        val probe1 = history.seriesFor("1")
        assertEquals(2, probe1.size)
        assertEquals(37L, probe1[0].latency)
        assertEquals(31L, probe1[0].low)
        assertEquals(52L, probe1[0].high)
        assertEquals(2, probe1[0].loss)
        // A bucket with no answer: no latency, no band, no loss.
        assertNull(probe1[1].latency)
        assertNull(probe1[1].low)
        assertNull(probe1[1].loss)

        // 37 and 88 answered; the median of the answered samples is the lower middle.
        assertEquals(37L, history.medianLatency)
        assertTrue(history.hasAnyLoss)
        assertEquals(1, history.metrics.size)
        assertEquals(33.3, history.metrics[0].cpu, 0.0001)
        assertEquals(500L, history.metrics[0].netRxMax)
    }

    @Test
    fun `unknown fields do not break the parse`() {
        val versions = json.decodeFromString(
            Versions.serializer(),
            """{"hub":"1.2.3","hub_latest":"1.2.4","agent_latest":"1.4.2","notice":true,"future":1}""",
        )
        assertEquals("1.2.3", versions.hub)
        assertEquals("1.2.4", versions.hubLatest)
    }

    @Test
    fun `a node without metrics still parses`() {
        val frame = json.decodeFromString(
            LiveFrame.serializer(),
            """{"admin":false,"nodes":[{"id":1,"name":"offline","online":false}]}""",
        )
        assertNull(frame.nodes.first().metrics)
        assertEquals("", frame.nodes.first().group.orEmpty())
    }
}
