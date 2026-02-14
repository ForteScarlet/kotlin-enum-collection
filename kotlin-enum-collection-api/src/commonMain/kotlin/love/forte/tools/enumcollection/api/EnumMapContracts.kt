package love.forte.tools.enumcollection.api

import kotlin.enums.EnumEntries

/**
 * Shared contract helpers for internal enum-map implementations.
 *
 * Keeping `equals`/`hashCode` logic centralized reduces duplication and makes it harder
 * for different implementations to accidentally diverge from the `Map` contract.
 */
internal fun <E : Enum<E>> enumMapEqualsByKeyBits(
    universe: EnumEntries<E>,
    keyBits: Int,
    slots: Array<Any?>,
    other: Any?
): Boolean {
    if (other !is Map<*, *>) return false

    val currentSize = keyBits.countOneBits()
    if (currentSize != other.size) return false

    if (other is EnumEntriesBasedI32EnumMap<*, *>) {
        if (!sameUniverse(universe, other.universe)) {
            return keyBits == 0 && other.keyBits == 0
        }
        if (keyBits != other.keyBits) return false

        var remaining = keyBits
        while (remaining != 0) {
            val ordinal = remaining.countTrailingZeroBits()
            if (slots[ordinal] != other.slots[ordinal]) return false
            remaining = remaining and (remaining - 1)
        }
        return true
    }

    var remaining = keyBits
    while (remaining != 0) {
        val ordinal = remaining.countTrailingZeroBits()
        val key = universe[ordinal]
        val value = slots[ordinal]
        val otherValue = other[key]
        if (otherValue != value || (value == null && !other.containsKey(key))) return false
        remaining = remaining and (remaining - 1)
    }
    return true
}

internal fun <E : Enum<E>> enumMapHashCodeByKeyBits(
    universe: EnumEntries<E>,
    keyBits: Int,
    slots: Array<Any?>
): Int {
    var hash = 0
    var remaining = keyBits
    while (remaining != 0) {
        val ordinal = remaining.countTrailingZeroBits()
        val keyHash = universe[ordinal].hashCode()
        val valueHash = slots[ordinal]?.hashCode() ?: 0
        hash += keyHash xor valueHash
        remaining = remaining and (remaining - 1)
    }
    return hash
}

internal fun <E : Enum<E>> enumMapEqualsByKeyBits(
    universe: EnumEntries<E>,
    keyBits: Long,
    slots: Array<Any?>,
    other: Any?
): Boolean {
    if (other !is Map<*, *>) return false

    val currentSize = keyBits.countOneBits()
    if (currentSize != other.size) return false

    if (other is EnumEntriesBasedI64EnumMap<*, *>) {
        if (!sameUniverse(universe, other.universe)) {
            return keyBits == 0L && other.keyBits == 0L
        }
        if (keyBits != other.keyBits) return false

        var remaining = keyBits
        while (remaining != 0L) {
            val ordinal = remaining.countTrailingZeroBits()
            if (slots[ordinal] != other.slots[ordinal]) return false
            remaining = remaining and (remaining - 1)
        }
        return true
    }

    var remaining = keyBits
    while (remaining != 0L) {
        val ordinal = remaining.countTrailingZeroBits()
        val key = universe[ordinal]
        val value = slots[ordinal]
        val otherValue = other[key]
        if (otherValue != value || (value == null && !other.containsKey(key))) return false
        remaining = remaining and (remaining - 1)
    }
    return true
}

internal fun <E : Enum<E>> enumMapHashCodeByKeyBits(
    universe: EnumEntries<E>,
    keyBits: Long,
    slots: Array<Any?>
): Int {
    var hash = 0
    var remaining = keyBits
    while (remaining != 0L) {
        val ordinal = remaining.countTrailingZeroBits()
        val keyHash = universe[ordinal].hashCode()
        val valueHash = slots[ordinal]?.hashCode() ?: 0
        hash += keyHash xor valueHash
        remaining = remaining and (remaining - 1)
    }
    return hash
}

internal fun <E : Enum<E>> enumMapEqualsByKeyWords(
    universe: EnumEntries<E>,
    keyWords: LongArray,
    slots: Array<Any?>,
    mapSize: Int,
    other: Any?
): Boolean {
    if (other !is Map<*, *>) return false
    if (mapSize != other.size) return false

    if (other is EnumEntriesBasedLargeEnumMap<*, *>) {
        if (!sameUniverse(universe, other.universe)) {
            return mapSize == 0
        }

        val leftWords = keyWords
        val rightWords = other.keyWords
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

        for (wordIndex in leftWords.indices) {
            var word = leftWords[wordIndex]
            while (word != 0L) {
                val bit = word.countTrailingZeroBits()
                val ordinal = (wordIndex shl 6) + bit
                val leftValue = if (ordinal < slots.size) slots[ordinal] else null
                val rightValue = if (ordinal < other.slots.size) other.slots[ordinal] else null
                if (leftValue != rightValue) return false
                word = word and (word - 1)
            }
        }

        return true
    }

    for (wordIndex in keyWords.indices) {
        var word = keyWords[wordIndex]
        while (word != 0L) {
            val bit = word.countTrailingZeroBits()
            val ordinal = (wordIndex shl 6) + bit
            val key = universe[ordinal]
            val value = if (ordinal < slots.size) slots[ordinal] else null
            val otherValue = other[key]
            if (otherValue != value || (value == null && !other.containsKey(key))) return false
            word = word and (word - 1)
        }
    }
    return true
}

internal fun <E : Enum<E>> enumMapHashCodeByKeyWords(
    universe: EnumEntries<E>,
    keyWords: LongArray,
    slots: Array<Any?>
): Int {
    var hash = 0
    for (wordIndex in keyWords.indices) {
        var word = keyWords[wordIndex]
        while (word != 0L) {
            val bit = word.countTrailingZeroBits()
            val ordinal = (wordIndex shl 6) + bit
            val keyHash = universe[ordinal].hashCode()
            val valueHash = if (ordinal < slots.size) (slots[ordinal]?.hashCode() ?: 0) else 0
            hash += keyHash xor valueHash
            word = word and (word - 1)
        }
    }
    return hash
}
