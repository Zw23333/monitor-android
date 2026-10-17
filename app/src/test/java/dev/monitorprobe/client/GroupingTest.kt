package dev.monitorprobe.client

import dev.monitorprobe.client.data.Node
import dev.monitorprobe.client.ui.GROUP_ALL
import dev.monitorprobe.client.ui.GROUP_NONE
import dev.monitorprobe.client.ui.countFor
import dev.monitorprobe.client.ui.groupKey
import dev.monitorprobe.client.ui.groupsOf
import dev.monitorprobe.client.ui.matchesGroup
import dev.monitorprobe.client.ui.sectionsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grouping rules are the panel's, not invented here: groups in first-appearance
 * order, the ungrouped run last, and a `=` prefix so a group actually named "all"
 * or "none" cannot collide with the two keywords.
 */
class GroupingTest {

    private fun node(id: Long, group: String? = null) =
        Node(id = id, name = "n$id", group = group)

    private val nodes = listOf(
        node(1, "日本"),
        node(2, null),
        node(3, "美国"),
        node(4, "日本"),
        node(5, ""),
    )

    @Test
    fun `groups appear in the order their first node does`() {
        assertEquals(listOf("日本", "美国"), groupsOf(nodes))
        // Reversing a two-group list is what actually demonstrates the rule: B's
        // first node now comes before A's. (Reversing `nodes` does not, because
        // 日本 still precedes 美国 there.)
        val pair = listOf(node(1, "A"), node(2, "B"))
        assertEquals(listOf("A", "B"), groupsOf(pair))
        assertEquals(listOf("B", "A"), groupsOf(pair.reversed()))
    }

    @Test
    fun `empty and null both mean ungrouped`() {
        assertTrue(matchesGroup(node(9, null), GROUP_NONE))
        assertTrue(matchesGroup(node(9, ""), GROUP_NONE))
        assertFalse(matchesGroup(node(9, "日本"), GROUP_NONE))
    }

    @Test
    fun `a group named like a keyword is not confused with it`() {
        val tricky = listOf(node(1, "all"), node(2, "none"), node(3, null))
        assertEquals(listOf("all", "none"), groupsOf(tricky))
        // "all" as a keyword selects everything; "=all" selects only that group.
        assertEquals(3, countFor(tricky, GROUP_ALL))
        assertEquals(1, countFor(tricky, groupKey("all")))
        assertEquals(1, countFor(tricky, groupKey("none")))
        assertEquals(1, countFor(tricky, GROUP_NONE))
    }

    @Test
    fun `sections put named groups first and the ungrouped run last`() {
        val sections = sectionsOf(nodes, GROUP_ALL)
        assertEquals(listOf("日本", "美国", "未分组"), sections.map { it.title })
        assertEquals(listOf(2, 1, 2), sections.map { it.nodes.size })
    }

    @Test
    fun `ungrouped gets no heading when there are no groups at all`() {
        val flat = listOf(node(1), node(2, ""))
        val sections = sectionsOf(flat, GROUP_ALL)
        assertEquals(1, sections.size)
        assertNull(sections.first().title)
        assertEquals(2, sections.first().nodes.size)
    }

    @Test
    fun `filtering to one group yields just that section`() {
        val sections = sectionsOf(nodes, groupKey("日本"))
        assertEquals(1, sections.size)
        assertEquals("日本", sections.first().title)
        assertEquals(listOf(1L, 4L), sections.first().nodes.map { it.id })
    }

    @Test
    fun `an empty selection yields no sections rather than an empty header`() {
        val sections = sectionsOf(nodes.filter { it.group == "美国" }, groupKey("日本"))
        assertTrue(sections.isEmpty())
    }
}
