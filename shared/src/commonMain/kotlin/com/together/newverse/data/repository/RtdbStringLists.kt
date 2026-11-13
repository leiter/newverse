package com.together.newverse.data.repository

/**
 * Read a list of strings out of whatever the Realtime Database hands back.
 *
 * RTDB has no list type. A dense array written as `["a", "b", "c"]` comes back as a
 * `List`, but the moment a hole appears — a seller deleting the middle option of a
 * picker — the same node comes back as a `Map` keyed `"0"`, `"2"`. Reading only one
 * of those shapes means a seller's edit silently empties every buyer's picker, which
 * is precisely the kind of failure this feature has had twice.
 *
 * So both are accepted and the map is sorted by its numeric key, which is the order
 * the seller wrote. A key that is not a number sorts last rather than throwing: it
 * is data nobody in this app writes, and losing the order is better than losing the
 * row.
 *
 * Blanks are dropped and entries are capped by the caller's [maxLength], so a row
 * written by hand cannot put an answer on screen that the rules would refuse to
 * accept back.
 */
internal fun stringListFromStored(raw: Any?, maxLength: Int): List<String> {
    val values: List<Any?> = when (raw) {
        is List<*> -> raw
        is Map<*, *> -> raw.entries
            .sortedBy { (key, _) -> (key as? String)?.toIntOrNull() ?: Int.MAX_VALUE }
            .map { it.value }
        else -> return emptyList()
    }
    return values.mapNotNull { value ->
        (value as? String)?.trim()?.take(maxLength)?.takeIf { it.isNotEmpty() }
    }
}
