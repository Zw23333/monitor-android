package dev.monitorprobe.client.ui

import dev.monitorprobe.client.data.Node

/**
 * Grouping, with the panel's own semantics.
 *
 * `group` is set by hand on the hub and is public; an empty one means ungrouped.
 * The panel encodes the selection as `"all"`, `"none"` for the ungrouped, or `"="`
 * followed by a group's name — the leading `=` is what keeps a group actually
 * named "all" or "none" from colliding with those two keywords, and the encoding
 * is reused here so the two clients agree on what a selection means.
 */
const val GROUP_ALL = "all"
const val GROUP_NONE = "none"

fun groupKey(name: String): String = "=$name"

/**
 * Every group in use, **in the order of the first node carrying it**: the node
 * order decides the group order, which is what makes the sections stable as nodes
 * come and go rather than reordering themselves alphabetically under the reader.
 */
fun groupsOf(nodes: List<Node>): List<String> {
    val seen = LinkedHashSet<String>()
    for (node in nodes) {
        val group = node.group?.takeIf { it.isNotBlank() } ?: continue
        seen.add(group)
    }
    return seen.toList()
}

fun matchesGroup(node: Node, selection: String): Boolean = when {
    selection == GROUP_ALL -> true
    selection == GROUP_NONE -> node.group.isNullOrBlank()
    selection.startsWith("=") -> node.group == selection.substring(1)
    else -> true
}

/** One rendered run of the list. A null [title] is a run that needs no heading. */
data class NodeSection(val title: String?, val nodes: List<Node>)

/**
 * The list as sections. Named groups come first, in [groupsOf] order; the
 * ungrouped nodes trail them and get a heading **only when there are named groups
 * to be told apart from** — a hub where nobody groups anything should not show a
 * "未分组" header over its only section.
 */
fun sectionsOf(nodes: List<Node>, selection: String): List<NodeSection> {
    val visible = nodes.filter { matchesGroup(it, selection) }
    if (visible.isEmpty()) return emptyList()

    val names = groupsOf(visible)
    val ungrouped = visible.filter { it.group.isNullOrBlank() }
    return buildList {
        names.forEach { name ->
            add(NodeSection(name, visible.filter { it.group == name }))
        }
        if (ungrouped.isNotEmpty()) {
            add(NodeSection(if (names.isEmpty()) null else "未分组", ungrouped))
        }
    }
}

/** How many nodes each selection would show, for the chips' counts. */
fun countFor(nodes: List<Node>, selection: String): Int = nodes.count { matchesGroup(it, selection) }
