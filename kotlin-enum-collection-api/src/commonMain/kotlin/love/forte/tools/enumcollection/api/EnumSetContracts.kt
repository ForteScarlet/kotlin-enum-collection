package love.forte.tools.enumcollection.api

import kotlin.enums.EnumEntries

/**
 * Shared contract helpers for internal enum-set implementations.
 *
 * These helpers keep `equals`/`hashCode` behavior consistent with the `Set` contract
 * while allowing specialized implementations to use universe-aware fast paths.
 */
internal fun <E : Enum<E>> enumSetEqualsByBits(values: EnumEntries<E>, bs: Int, other: Any?): Boolean {
    if (other !is Set<*>) return false

    if (other is EnumEntriesBasedI32EnumSet<*>) {
        if (!sameUniverse(values, other.values)) {
            return bs == 0 && other.bs == 0
        }
        return bs == other.bs
    }

    val currentBits = bs
    if (other.size != currentBits.countOneBits()) return false
    for (element in other) {
        val ordinal = ordinalInUniverseOrMinusOne(element, values)
        if (ordinal < 0 || (currentBits and (1 shl ordinal)) == 0) return false
    }
    return true
}

internal fun <E : Enum<E>> enumSetHashCodeByBits(values: EnumEntries<E>, bs: Int): Int {
    var hash = 0
    var remaining = bs
    while (remaining != 0) {
        val bit = remaining.countTrailingZeroBits()
        hash += values[bit].hashCode()
        remaining = remaining and (remaining - 1)
    }
    return hash
}

internal fun <E : Enum<E>> enumSetEqualsByBits(values: EnumEntries<E>, bs: Long, other: Any?): Boolean {
    if (other !is Set<*>) return false

    if (other is EnumEntriesBasedI64EnumSet<*>) {
        if (!sameUniverse(values, other.values)) {
            return bs == 0L && other.bs == 0L
        }
        return bs == other.bs
    }

    val currentBits = bs
    if (other.size != currentBits.countOneBits()) return false
    for (element in other) {
        val ordinal = ordinalInUniverseOrMinusOne(element, values)
        if (ordinal < 0 || (currentBits and (1L shl ordinal)) == 0L) return false
    }
    return true
}

internal fun <E : Enum<E>> enumSetHashCodeByBits(values: EnumEntries<E>, bs: Long): Int {
    var hash = 0
    var remaining = bs
    while (remaining != 0L) {
        val bit = remaining.countTrailingZeroBits()
        hash += values[bit].hashCode()
        remaining = remaining and (remaining - 1)
    }
    return hash
}

internal fun <E : Enum<E>> enumSetEqualsByWords(values: EnumEntries<E>, bs: LongArray, other: Any?): Boolean {
    if (other !is Set<*>) return false

    if (other is EnumEntriesBasedLargeEnumSet<*>) {
        if (!sameUniverse(values, other.values)) {
            return bs.isEmpty() && other.bs.isEmpty()
        }

        val leftWords = bs
        val rightWords = other.bs
        val minSize = minOf(leftWords.size, rightWords.size)
        for (wordIndex in 0 until minSize) {
            if (leftWords[wordIndex] != rightWords[wordIndex]) return false
        }
        for (wordIndex in minSize until leftWords.size) {
            if (leftWords[wordIndex] != 0L) return false
        }
        for (wordIndex in minSize until rightWords.size) {
            if (rightWords[wordIndex] != 0L) return false
        }
        return true
    }

    if (other.size != bitCountOf(bs)) return false
    for (element in other) {
        val ordinal = ordinalInUniverseOrMinusOne(element, values)
        if (ordinal < 0) return false
        val wordIndex = ordinal ushr 6
        if (wordIndex >= bs.size) return false
        if ((bs[wordIndex] and (1L shl (ordinal and 63))) == 0L) return false
    }
    return true
}

internal fun <E : Enum<E>> enumSetHashCodeByWords(values: EnumEntries<E>, bs: LongArray): Int {
    var hash = 0
    for (wordIndex in bs.indices) {
        var word = bs[wordIndex]
        while (word != 0L) {
            val bit = word.countTrailingZeroBits()
            hash += values[(wordIndex shl 6) + bit].hashCode()
            word = word and (word - 1)
        }
    }
    return hash
}
