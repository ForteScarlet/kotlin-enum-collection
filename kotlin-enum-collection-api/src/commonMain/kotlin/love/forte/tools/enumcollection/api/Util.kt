package love.forte.tools.enumcollection.api

import kotlin.enums.EnumEntries

/**
 * Checks whether two enum universes refer to the same enum type.
 *
 * This is an optimization-only helper used by internal implementations to choose fast paths.
 */
internal fun sameUniverse(left: EnumEntries<*>, right: EnumEntries<*>): Boolean {
    if (left === right) return true
    val size = left.size
    if (size != right.size) return false
    if (size == 0) return true
    return left[0] === right[0] && left[size - 1] === right[size - 1]
}

internal fun bitCountOf(words: LongArray): Int {
    var count = 0
    for (word in words) {
        count += word.countOneBits()
    }
    return count
}

internal fun lastNonZeroIndex(words: LongArray): Int {
    var index = words.size - 1
    while (index >= 0 && words[index] == 0L) {
        index--
    }
    return index
}

internal fun trimByLastNonZero(words: LongArray, lastNonZeroWordIndex: Int): LongArray = when {
    lastNonZeroWordIndex < 0 -> LongArray(0)
    lastNonZeroWordIndex == words.lastIndex -> words
    else -> words.copyOf(lastNonZeroWordIndex + 1)
}
