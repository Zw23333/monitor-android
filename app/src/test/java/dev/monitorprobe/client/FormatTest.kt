package dev.monitorprobe.client

import dev.monitorprobe.client.data.HubState
import dev.monitorprobe.client.data.Metrics
import dev.monitorprobe.client.data.Node
import dev.monitorprobe.client.format.billingCycleName
import dev.monitorprobe.client.format.countryFlag
import dev.monitorprobe.client.format.formatBytes
import dev.monitorprobe.client.format.formatRate
import dev.monitorprobe.client.format.formatUptime
import dev.monitorprobe.client.format.relativeSeconds
import dev.monitorprobe.client.format.virtName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FormatTest {

    @Test
    fun `bytes use binary units and one decimal under ten`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KiB", formatBytes(1024))
        assertEquals("1.5 KiB", formatBytes(1536))
        assertEquals("10 MiB", formatBytes(10L * 1024 * 1024))
        assertEquals("1.0 GiB", formatBytes(1024L * 1024 * 1024))
        assertEquals("1.0 TiB", formatBytes(1024L * 1024 * 1024 * 1024))
    }

    @Test
    fun `rates carry a per-second suffix`() {
        assertEquals("1.0 KiB/s", formatRate(1024))
        assertEquals("0 B/s", formatRate(0))
    }

    @Test
    fun `uptime keeps the two largest units`() {
        assertEquals("2天3小时", formatUptime(2 * 86_400 + 3 * 3_600 + 59))
        assertEquals("5小时7分", formatUptime(5 * 3_600 + 7 * 60))
        assertEquals("9分", formatUptime(9 * 60 + 30))
        assertEquals("—", formatUptime(0))
    }

    @Test
    fun `country codes become flags and anything else stays blank`() {
        assertEquals("\uD83C\uDDEF\uD83C\uDDF5", countryFlag("JP"))
        assertEquals("\uD83C\uDDE8\uD83C\uDDF3", countryFlag("cn"))
        assertEquals("", countryFlag(""))
        assertEquals("", countryFlag("JPN"))
        assertEquals("", countryFlag("1P"))
    }

    @Test
    fun `relative seconds read as the panel writes them`() {
        assertEquals("从未上报", relativeSeconds(null))
        assertEquals("30 秒前", relativeSeconds(30))
        assertEquals("5 分钟前", relativeSeconds(5 * 60))
        assertEquals("2 小时前", relativeSeconds(2 * 3_600))
        assertEquals("3 天前", relativeSeconds(3 * 86_400))
    }

    @Test
    fun `billing cycles and virt names are translated`() {
        assertEquals("月付", billingCycleName("monthly"))
        assertEquals("年付", billingCycleName("annually"))
        assertEquals("一次性", billingCycleName("one-time"))
        assertEquals("custom", billingCycleName("custom"))
        assertEquals("KVM", virtName("kvm"))
        assertEquals("独服", virtName("none"))
        assertEquals("独服", virtName(""))
    }

    @Test
    fun `the rollup folds online counts and traffic in one pass`() {
        val state = HubState(
            nodes = listOf(
                Node(id = 1, online = true, monthUsed = 100, trafficLimit = 1000,
                    metrics = Metrics(netRx = 10, netTx = 20)),
                Node(id = 2, online = false, monthUsed = 50, trafficLimit = 500,
                    metrics = Metrics(netRx = 999, netTx = 999)),
                Node(id = 3, online = true, monthUsed = 25, trafficLimit = 0,
                    metrics = null),
            ),
        )
        val rollup = state.rollup
        assertEquals(2, rollup.online)
        assertEquals(1, rollup.offline)
        // Only online nodes count toward current throughput.
        assertEquals(10L, rollup.netRx)
        assertEquals(20L, rollup.netTx)
        assertEquals(175L, rollup.monthUsed)
        assertEquals(1500L, rollup.monthLimit)

        val summary = state.summary("site")
        assertEquals(3, summary.total)
        assertEquals(2, summary.online)
        assertEquals(1, summary.offline)
        assertEquals("site", summary.siteName)
    }

    @Test
    fun `metrics fractions never exceed one`() {
        val metrics = Metrics(memTotal = 100, memUsed = 150, diskTotal = 0, diskUsed = 5)
        assertEquals(1f, metrics.memFraction, 0.0001f)
        assertEquals(0f, metrics.diskFraction, 0.0001f)
        assertNotNull(metrics)
    }
}
