package love.forte.tools.enumcollection.api

import kotlin.enums.EnumEntries
import kotlin.enums.enumEntries

/**
 * A set specialized for enum elements.
 *
 * Implementations use ordinal-based bit storage to minimize memory footprint and branch cost.
 *
 * Example:
 * ```kotlin
 * enum class Role { USER, ADMIN, GUEST }
 *
 * val set = enumSetOf(Role.USER, Role.ADMIN)
 * val hasUser = set.contains(Role.USER) // true
 * ```
 */
public interface EnumSet<E : Enum<E>> : Set<E> {
    /**
     * Returns `true` when this set contains at least one element from [elements].
     *
     * Example: `enumSetOf(Role.USER).containsAny(listOf(Role.ADMIN, Role.USER)) == true`
     */
    public fun containsAny(elements: Collection<E>): Boolean

    /**
     * Returns a new set containing elements that are in both this set and [other].
     *
     * Example: `enumSetOf(Role.USER, Role.ADMIN).intersect(setOf(Role.ADMIN))`
     */
    public fun intersect(other: Set<E>): Set<E>

    /**
     * Returns a new set containing elements that are in this set or [other].
     *
     * Example: `enumSetOf(Role.USER).union(setOf(Role.ADMIN))`
     */
    public fun union(other: Set<E>): Set<E>

    /**
     * Returns a new set containing elements in this set but not in [other].
     *
     * Example: `enumSetOf(Role.USER, Role.ADMIN).difference(setOf(Role.USER))`
     */
    public fun difference(other: Set<E>): Set<E>
}

/**
 * Converts this set to an immutable [EnumSet].
 *
 * For internal enum-set implementations, this method uses zero/low-copy fast paths.
 *
 * Example:
 * ```kotlin
 * val mutable = mutableEnumSetOf(Role.USER)
 * val snapshot = mutable.toEnumSet()
 * mutable.add(Role.ADMIN)
 * // snapshot still only contains USER
 * ```
 */
public fun <E : Enum<E>> Set<E>.toEnumSet(): EnumSet<E> {
    if (this is EnumSet<E> && this !is MutableEnumSet<E>) return this

    @Suppress("UNCHECKED_CAST")
    return when (this) {
        is EnumEntriesBasedI32EnumSet<*> -> {
            val set = this as EnumEntriesBasedI32EnumSet<E>
            createI32EnumSet(set.bs, set.values)
        }

        is EnumEntriesBasedI64EnumSet<*> -> {
            val set = this as EnumEntriesBasedI64EnumSet<E>
            createI64EnumSet(set.bs, set.values)
        }

        is EnumEntriesBasedLargeEnumSet<*> -> {
            val set = this as EnumEntriesBasedLargeEnumSet<E>
            createLargeEnumSet(set.bs.copyOf(), set.values)
        }

        else -> {
            if (isEmpty()) {
                emptyEnumSetOf()
            } else {
                GenericEnumSet(toSet())
            }
        }
    }
}

/**
 * Creates an [EnumSet] that contains all constants of enum type [E].
 *
 * Example: `fullEnumSetOf<Role>()`
 */
public inline fun <reified E : Enum<E>> fullEnumSetOf(): EnumSet<E> {
    val entries = enumEntries<E>()
    val entrySize = entries.size
    return when {
        entrySize == 0 -> emptyEnumSetOf()
        entrySize <= Int.SIZE_BITS -> {
            val bits = if (entrySize == Int.SIZE_BITS) -1 else (1 shl entrySize) - 1
            createI32EnumSet(bits, entries)
        }

        entrySize <= Long.SIZE_BITS -> {
            val bits = if (entrySize == Long.SIZE_BITS) -1L else (1L shl entrySize) - 1L
            createI64EnumSet(bits, entries)
        }

        else -> {
            val wordSize = (entrySize + 63) ushr 6
            val words = LongArray(wordSize) { -1L }
            val tail = entrySize and 63
            if (tail != 0) {
                words[wordSize - 1] = (1L shl tail) - 1L
            }
            createLargeEnumSet(words, entries)
        }
    }
}

/**
 * Returns an immutable empty [EnumSet].
 *
 * Example: `emptyEnumSetOf<Role>().isEmpty()`
 */
@Suppress("UNCHECKED_CAST")
public fun <E : Enum<E>> emptyEnumSetOf(): EnumSet<E> = EmptyEnumSet as EnumSet<E>

/**
 * Creates an immutable [EnumSet] containing [elements].
 *
 * Example: `enumSetOf(Role.USER, Role.ADMIN)`
 */
public inline fun <reified E : Enum<E>> enumSetOf(vararg elements: E): EnumSet<E> {
    val entries = enumEntries<E>()
    val entrySize = entries.size
    return when {
        entrySize == 0 -> emptyEnumSetOf()
        entrySize <= Int.SIZE_BITS -> createI32EnumSet(calculateI32BitSize(elements), entries)
        entrySize <= Long.SIZE_BITS -> createI64EnumSet(calculateI64BitSize(elements), entries)
        else -> createLargeEnumSet(calculateLargeBitSize(entrySize, elements), entries)
    }
}

@PublishedApi
internal fun <E : Enum<E>> calculateI32BitSize(elements: Array<out E>): Int {
    if (elements.isEmpty()) return 0
    var bits = 0
    for (element in elements) {
        bits = bits or (1 shl element.ordinal)
    }
    return bits
}

@PublishedApi
internal fun <E : Enum<E>> calculateI64BitSize(elements: Array<out E>): Long {
    if (elements.isEmpty()) return 0L
    var bits = 0L
    for (element in elements) {
        bits = bits or (1L shl element.ordinal)
    }
    return bits
}

@PublishedApi
internal fun <E : Enum<E>> calculateLargeBitSize(total: Int, elements: Array<out E>): LongArray {
    if (elements.isEmpty()) return LongArray(0)

    var maxOrdinal = 0
    for (element in elements) {
        val ordinal = element.ordinal
        if (ordinal > maxOrdinal) maxOrdinal = ordinal
        if (maxOrdinal == total - 1) break
    }

    val maxWordSize = (total + 63) ushr 6
    val wordSize = minOf(maxWordSize, (maxOrdinal + 64) ushr 6)
    val words = LongArray(wordSize)
    for (element in elements) {
        val ordinal = element.ordinal
        val wordIndex = ordinal ushr 6
        words[wordIndex] = words[wordIndex] or (1L shl (ordinal and 63))
    }
    return words
}

@PublishedApi
internal fun <E : Enum<E>> createI32EnumSet(bs: Int, values: EnumEntries<E>): EnumSet<E> {
    if (bs == 0) return emptyEnumSetOf()
    return I32EnumSet(bs, values)
}

@PublishedApi
internal fun <E : Enum<E>> createI64EnumSet(bs: Long, values: EnumEntries<E>): EnumSet<E> {
    if (bs == 0L) return emptyEnumSetOf()
    return I64EnumSet(bs, values)
}

@PublishedApi
internal fun <E : Enum<E>> createLargeEnumSet(bs: LongArray, values: EnumEntries<E>): EnumSet<E> {
    val lastNonZeroWordIndex = lastNonZeroIndex(bs)
    if (lastNonZeroWordIndex < 0) return emptyEnumSetOf()
    val words = trimByLastNonZero(bs, lastNonZeroWordIndex)
    return LargeEnumSet(words, values)
}

internal fun <E : Enum<E>> ordinalInUniverseOrMinusOne(element: Any?, values: EnumEntries<E>): Int {
    if (element !is Enum<*>) return -1
    val ordinal = element.ordinal
    if (ordinal < 0 || ordinal >= values.size) return -1
    return if (values[ordinal] === element) ordinal else -1
}

internal fun bitCountOf(words: LongArray): Int {
    var count = 0
    for (word in words) {
        count += word.countOneBits()
    }
    return count
}

internal fun <E : Enum<E>> containsByOrdinal(bits: Int, values: EnumEntries<E>, element: Any?): Boolean {
    val ordinal = ordinalInUniverseOrMinusOne(element, values)
    return ordinal >= 0 && (bits and (1 shl ordinal)) != 0
}

internal fun <E : Enum<E>> containsByOrdinal(bits: Long, values: EnumEntries<E>, element: Any?): Boolean {
    val ordinal = ordinalInUniverseOrMinusOne(element, values)
    return ordinal >= 0 && (bits and (1L shl ordinal)) != 0L
}

internal fun <E : Enum<E>> containsByOrdinal(words: LongArray, values: EnumEntries<E>, element: Any?): Boolean {
    val ordinal = ordinalInUniverseOrMinusOne(element, values)
    if (ordinal < 0) return false
    val wordIndex = ordinal ushr 6
    return wordIndex < words.size && (words[wordIndex] and (1L shl (ordinal and 63))) != 0L
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

private object EmptyEnumSet : EnumSet<Nothing> {
    override fun containsAny(elements: Collection<Nothing>): Boolean = false

    override fun intersect(other: Set<Nothing>): Set<Nothing> = EmptyEnumSet

    override fun union(other: Set<Nothing>): Set<Nothing> =
        if (other.isEmpty()) EmptyEnumSet else GenericEnumSet(other.toSet())

    override fun difference(other: Set<Nothing>): Set<Nothing> = EmptyEnumSet

    override fun contains(element: Nothing): Boolean = false
    override fun containsAll(elements: Collection<Nothing>): Boolean = elements.isEmpty()
    override fun isEmpty(): Boolean = true
    override fun iterator(): Iterator<Nothing> = emptySet<Nothing>().iterator()
    override val size: Int get() = 0
}

internal sealed interface EnumEntriesBasedEnumSet<E : Enum<E>> : EnumSet<E> {
    val values: EnumEntries<E>
}

internal interface EnumEntriesBasedI32EnumSet<E : Enum<E>> : EnumEntriesBasedEnumSet<E> {
    val bs: Int
}

internal interface EnumEntriesBasedI64EnumSet<E : Enum<E>> : EnumEntriesBasedEnumSet<E> {
    val bs: Long
}

internal interface EnumEntriesBasedLargeEnumSet<E : Enum<E>> : EnumEntriesBasedEnumSet<E> {
    val bs: LongArray
}

internal class GenericEnumSet<E : Enum<E>>(private val delegate: Set<E>) : EnumSet<E>, Set<E> by delegate {
    override fun containsAny(elements: Collection<E>): Boolean {
        if (delegate.isEmpty() || elements.isEmpty()) return false
        if (elements is Set<*> && elements.size < delegate.size) {
            for (element in elements) {
                if (delegate.contains(element)) return true
            }
            return false
        }
        for (element in delegate) {
            if (elements.contains(element)) return true
        }
        return false
    }

    override fun intersect(other: Set<E>): Set<E> {
        if (delegate.isEmpty() || other.isEmpty()) return emptyEnumSetOf()
        if (other === this) return this

        val result = LinkedHashSet<E>(minOf(delegate.size, other.size))
        for (element in delegate) {
            if (other.contains(element)) {
                result.add(element)
            }
        }
        if (result.isEmpty()) return emptyEnumSetOf()
        return GenericEnumSet(result)
    }

    override fun union(other: Set<E>): Set<E> {
        if (other.isEmpty()) return this
        if (other === this) return this

        val result = LinkedHashSet<E>(delegate.size + other.size)
        result.addAll(delegate)
        result.addAll(other)
        return GenericEnumSet(result)
    }

    override fun difference(other: Set<E>): Set<E> {
        if (delegate.isEmpty()) return emptyEnumSetOf()
        if (other.isEmpty()) return this
        if (other === this) return emptyEnumSetOf()

        val result = LinkedHashSet<E>(delegate.size)
        for (element in delegate) {
            if (!other.contains(element)) {
                result.add(element)
            }
        }
        if (result.isEmpty()) return emptyEnumSetOf()
        return GenericEnumSet(result)
    }

    override fun equals(other: Any?): Boolean = delegate == other

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = delegate.toString()
}

private class I32EnumSet<E : Enum<E>>(
    override val bs: Int,
    override val values: EnumEntries<E>
) : EnumEntriesBasedI32EnumSet<E> {
    override fun contains(element: E): Boolean = (bs and (1 shl element.ordinal)) != 0

    override fun containsAll(elements: Collection<E>): Boolean {
        if (elements.isEmpty()) return true

        if (elements is EnumEntriesBasedI32EnumSet<*>) {
            if (!sameUniverse(values, elements.values)) return elements.isEmpty()
            val otherBits = elements.bs
            return (bs and otherBits) == otherBits
        }

        val currentBits = bs
        if (currentBits == 0) return false
        if (elements is Set<*> && elements.size > currentBits.countOneBits()) return false

        for (element in elements) {
            if (!containsByOrdinal(currentBits, values, element)) return false
        }

        return true
    }

    override fun containsAny(elements: Collection<E>): Boolean {
        if (elements.isEmpty()) return false

        if (elements is EnumEntriesBasedI32EnumSet<*>) {
            if (!sameUniverse(values, elements.values)) return false
            return (bs and elements.bs) != 0
        }

        val currentBits = bs
        if (currentBits == 0) return false

        for (element in elements) {
            if (containsByOrdinal(currentBits, values, element)) return true
        }

        return false
    }

    override fun intersect(other: Set<E>): Set<E> {
        val currentBits = bs
        if (currentBits == 0 || other.isEmpty()) return emptyEnumSetOf()
        if (other === this) return this

        if (other is EnumEntriesBasedI32EnumSet<*>) {
            if (!sameUniverse(values, other.values)) return emptyEnumSetOf()
            val intersectBits = currentBits and other.bs
            if (intersectBits == currentBits) return this
            return createI32EnumSet(intersectBits, values)
        }

        var intersectBits = 0
        if (other.size < currentBits.countOneBits()) {
            for (element in other) {
                val ordinal = ordinalInUniverseOrMinusOne(element, values)
                if (ordinal < 0) continue
                val mask = 1 shl ordinal
                if ((currentBits and mask) != 0) {
                    intersectBits = intersectBits or mask
                }
            }
        } else {
            var remaining = currentBits
            while (remaining != 0) {
                val bit = remaining.countTrailingZeroBits()
                if (other.contains(values[bit])) {
                    intersectBits = intersectBits or (1 shl bit)
                }
                remaining = remaining and (remaining - 1)
            }
        }

        if (intersectBits == currentBits) return this
        return createI32EnumSet(intersectBits, values)
    }

    override fun union(other: Set<E>): Set<E> {
        val currentBits = bs
        if (other.isEmpty()) return this
        if (other === this) return this

        if (other is EnumEntriesBasedI32EnumSet<*>) {
            if (!sameUniverse(values, other.values)) return this
            val mergedBits = currentBits or other.bs
            if (mergedBits == currentBits) return this
            return createI32EnumSet(mergedBits, values)
        }

        var mergedBits = currentBits
        for (element in other) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal >= 0) {
                mergedBits = mergedBits or (1 shl ordinal)
            }
        }

        if (mergedBits == currentBits) return this
        return createI32EnumSet(mergedBits, values)
    }

    override fun difference(other: Set<E>): Set<E> {
        val currentBits = bs
        if (currentBits == 0 || other.isEmpty()) return this
        if (other === this) return emptyEnumSetOf()

        if (other is EnumEntriesBasedI32EnumSet<*>) {
            if (!sameUniverse(values, other.values)) return this
            val differenceBits = currentBits and other.bs.inv()
            if (differenceBits == currentBits) return this
            return createI32EnumSet(differenceBits, values)
        }

        var removeMask = 0
        for (element in other) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal >= 0) {
                removeMask = removeMask or (1 shl ordinal)
            }
        }

        if (removeMask == 0) return this
        val differenceBits = currentBits and removeMask.inv()
        if (differenceBits == currentBits) return this
        return createI32EnumSet(differenceBits, values)
    }

    override fun isEmpty(): Boolean = bs == 0

    override fun iterator(): Iterator<E> = object : Iterator<E> {
        private var remaining = bs

        override fun hasNext(): Boolean = remaining != 0

        override fun next(): E {
            val current = remaining
            if (current == 0) throw NoSuchElementException()
            val bit = current.countTrailingZeroBits()
            remaining = current and (current - 1)
            return values[bit]
        }
    }

    override val size: Int
        get() = bs.countOneBits()

    override fun toString(): String = joinToString(", ", "[", "]")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
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

    override fun hashCode(): Int {
        var hash = 0
        var remaining = bs
        while (remaining != 0) {
            val bit = remaining.countTrailingZeroBits()
            hash += values[bit].hashCode()
            remaining = remaining and (remaining - 1)
        }
        return hash
    }
}

private class I64EnumSet<E : Enum<E>>(
    override val bs: Long,
    override val values: EnumEntries<E>
) : EnumEntriesBasedI64EnumSet<E> {
    override fun contains(element: E): Boolean = (bs and (1L shl element.ordinal)) != 0L

    override fun containsAll(elements: Collection<E>): Boolean {
        if (elements.isEmpty()) return true

        if (elements is EnumEntriesBasedI64EnumSet<*>) {
            if (!sameUniverse(values, elements.values)) return elements.isEmpty()
            val otherBits = elements.bs
            return (bs and otherBits) == otherBits
        }

        val currentBits = bs
        if (currentBits == 0L) return false
        if (elements is Set<*> && elements.size > currentBits.countOneBits()) return false

        for (element in elements) {
            if (!containsByOrdinal(currentBits, values, element)) return false
        }

        return true
    }

    override fun containsAny(elements: Collection<E>): Boolean {
        if (elements.isEmpty()) return false

        if (elements is EnumEntriesBasedI64EnumSet<*>) {
            if (!sameUniverse(values, elements.values)) return false
            return (bs and elements.bs) != 0L
        }

        val currentBits = bs
        if (currentBits == 0L) return false

        for (element in elements) {
            if (containsByOrdinal(currentBits, values, element)) return true
        }

        return false
    }

    override fun intersect(other: Set<E>): Set<E> {
        val currentBits = bs
        if (currentBits == 0L || other.isEmpty()) return emptyEnumSetOf()
        if (other === this) return this

        if (other is EnumEntriesBasedI64EnumSet<*>) {
            if (!sameUniverse(values, other.values)) return emptyEnumSetOf()
            val intersectBits = currentBits and other.bs
            if (intersectBits == currentBits) return this
            return createI64EnumSet(intersectBits, values)
        }

        var intersectBits = 0L
        if (other.size < currentBits.countOneBits()) {
            for (element in other) {
                val ordinal = ordinalInUniverseOrMinusOne(element, values)
                if (ordinal < 0) continue
                val mask = 1L shl ordinal
                if ((currentBits and mask) != 0L) {
                    intersectBits = intersectBits or mask
                }
            }
        } else {
            var remaining = currentBits
            while (remaining != 0L) {
                val bit = remaining.countTrailingZeroBits()
                if (other.contains(values[bit])) {
                    intersectBits = intersectBits or (1L shl bit)
                }
                remaining = remaining and (remaining - 1)
            }
        }

        if (intersectBits == currentBits) return this
        return createI64EnumSet(intersectBits, values)
    }

    override fun union(other: Set<E>): Set<E> {
        val currentBits = bs
        if (other.isEmpty()) return this
        if (other === this) return this

        if (other is EnumEntriesBasedI64EnumSet<*>) {
            if (!sameUniverse(values, other.values)) return this
            val mergedBits = currentBits or other.bs
            if (mergedBits == currentBits) return this
            return createI64EnumSet(mergedBits, values)
        }

        var mergedBits = currentBits
        for (element in other) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal >= 0) {
                mergedBits = mergedBits or (1L shl ordinal)
            }
        }

        if (mergedBits == currentBits) return this
        return createI64EnumSet(mergedBits, values)
    }

    override fun difference(other: Set<E>): Set<E> {
        val currentBits = bs
        if (currentBits == 0L || other.isEmpty()) return this
        if (other === this) return emptyEnumSetOf()

        if (other is EnumEntriesBasedI64EnumSet<*>) {
            if (!sameUniverse(values, other.values)) return this
            val differenceBits = currentBits and other.bs.inv()
            if (differenceBits == currentBits) return this
            return createI64EnumSet(differenceBits, values)
        }

        var removeMask = 0L
        for (element in other) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal >= 0) {
                removeMask = removeMask or (1L shl ordinal)
            }
        }

        if (removeMask == 0L) return this
        val differenceBits = currentBits and removeMask.inv()
        if (differenceBits == currentBits) return this
        return createI64EnumSet(differenceBits, values)
    }

    override fun isEmpty(): Boolean = bs == 0L

    override fun iterator(): Iterator<E> = object : Iterator<E> {
        private var remaining = bs

        override fun hasNext(): Boolean = remaining != 0L

        override fun next(): E {
            val current = remaining
            if (current == 0L) throw NoSuchElementException()
            val bit = current.countTrailingZeroBits()
            remaining = current and (current - 1)
            return values[bit]
        }
    }

    override val size: Int
        get() = bs.countOneBits()

    override fun toString(): String = joinToString(", ", "[", "]")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
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

    override fun hashCode(): Int {
        var hash = 0
        var remaining = bs
        while (remaining != 0L) {
            val bit = remaining.countTrailingZeroBits()
            hash += values[bit].hashCode()
            remaining = remaining and (remaining - 1)
        }
        return hash
    }
}

private class LargeEnumSet<E : Enum<E>>(
    override val bs: LongArray,
    override val values: EnumEntries<E>
) : EnumEntriesBasedLargeEnumSet<E> {
    override fun contains(element: E): Boolean {
        val ordinal = element.ordinal
        val wordIndex = ordinal ushr 6
        return wordIndex < bs.size && (bs[wordIndex] and (1L shl (ordinal and 63))) != 0L
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        if (elements.isEmpty()) return true

        if (elements is EnumEntriesBasedLargeEnumSet<*>) {
            if (!sameUniverse(values, elements.values)) return elements.isEmpty()
            val otherWords = elements.bs
            if (otherWords.size > bs.size) return false
            for (wordIndex in otherWords.indices) {
                val otherWord = otherWords[wordIndex]
                if ((bs[wordIndex] and otherWord) != otherWord) return false
            }
            return true
        }

        if (bs.isEmpty()) return false
        val currentWords = bs
        for (element in elements) {
            if (!containsByOrdinal(currentWords, values, element)) return false
        }
        return true
    }

    override fun containsAny(elements: Collection<E>): Boolean {
        if (elements.isEmpty() || bs.isEmpty()) return false

        if (elements is EnumEntriesBasedLargeEnumSet<*>) {
            if (!sameUniverse(values, elements.values)) return false
            val otherWords = elements.bs
            val minSize = minOf(bs.size, otherWords.size)
            for (wordIndex in 0 until minSize) {
                if ((bs[wordIndex] and otherWords[wordIndex]) != 0L) return true
            }
            return false
        }

        val currentWords = bs
        for (element in elements) {
            if (containsByOrdinal(currentWords, values, element)) return true
        }
        return false
    }

    override fun intersect(other: Set<E>): Set<E> {
        if (bs.isEmpty() || other.isEmpty()) return emptyEnumSetOf()
        if (other === this) return this

        if (other is EnumEntriesBasedLargeEnumSet<*>) {
            if (!sameUniverse(values, other.values)) return emptyEnumSetOf()
            val otherWords = other.bs
            val minSize = minOf(bs.size, otherWords.size)
            val resultWords = LongArray(minSize)
            var lastNonZeroWordIndex = -1
            for (wordIndex in 0 until minSize) {
                val word = bs[wordIndex] and otherWords[wordIndex]
                resultWords[wordIndex] = word
                if (word != 0L) lastNonZeroWordIndex = wordIndex
            }
            return createLargeEnumSet(trimByLastNonZero(resultWords, lastNonZeroWordIndex), values)
        }

        val currentWords = bs
        val resultWords = LongArray(currentWords.size)
        var lastNonZeroWordIndex = -1

        if (other.size < size) {
            for (element in other) {
                val ordinal = ordinalInUniverseOrMinusOne(element, values)
                if (ordinal < 0) continue
                val wordIndex = ordinal ushr 6
                if (wordIndex >= currentWords.size) continue
                val bitMask = 1L shl (ordinal and 63)
                if ((currentWords[wordIndex] and bitMask) == 0L) continue
                resultWords[wordIndex] = resultWords[wordIndex] or bitMask
                if (wordIndex > lastNonZeroWordIndex) lastNonZeroWordIndex = wordIndex
            }
        } else {
            for (wordIndex in currentWords.indices) {
                var word = currentWords[wordIndex]
                while (word != 0L) {
                    val bit = word.countTrailingZeroBits()
                    if (other.contains(values[(wordIndex shl 6) + bit])) {
                        resultWords[wordIndex] = resultWords[wordIndex] or (1L shl bit)
                        lastNonZeroWordIndex = wordIndex
                    }
                    word = word and (word - 1)
                }
            }
        }

        return createLargeEnumSet(trimByLastNonZero(resultWords, lastNonZeroWordIndex), values)
    }

    override fun union(other: Set<E>): Set<E> {
        if (other.isEmpty()) return this
        if (other === this) return this

        if (other is EnumEntriesBasedLargeEnumSet<*>) {
            if (!sameUniverse(values, other.values)) return this
            val currentWords = bs
            val otherWords = other.bs
            if (otherWords.size <= currentWords.size) {
                val resultWords = currentWords.copyOf()
                var changed = false
                for (wordIndex in otherWords.indices) {
                    val oldWord = resultWords[wordIndex]
                    val mergedWord = oldWord or otherWords[wordIndex]
                    if (mergedWord != oldWord) {
                        resultWords[wordIndex] = mergedWord
                        changed = true
                    }
                }
                if (!changed) return this
                return createLargeEnumSet(resultWords, values)
            }

            val resultWords = otherWords.copyOf()
            var changed = false
            for (wordIndex in currentWords.indices) {
                val oldWord = resultWords[wordIndex]
                val mergedWord = oldWord or currentWords[wordIndex]
                if (mergedWord != oldWord) {
                    resultWords[wordIndex] = mergedWord
                    changed = true
                }
            }
            var tailHasAdditionalBits = false
            for (wordIndex in currentWords.size until resultWords.size) {
                if (resultWords[wordIndex] != 0L) {
                    tailHasAdditionalBits = true
                    break
                }
            }
            if (!changed && !tailHasAdditionalBits) return this
            return createLargeEnumSet(resultWords, values)
        }

        var resultWords = bs.copyOf()
        var changed = false
        for (element in other) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal < 0) continue
            val wordIndex = ordinal ushr 6
            if (wordIndex >= resultWords.size) {
                resultWords = resultWords.copyOf(wordIndex + 1)
                changed = true
            }
            val oldWord = resultWords[wordIndex]
            val newWord = oldWord or (1L shl (ordinal and 63))
            if (newWord != oldWord) {
                resultWords[wordIndex] = newWord
                changed = true
            }
        }

        if (!changed) return this
        return createLargeEnumSet(resultWords, values)
    }

    override fun difference(other: Set<E>): Set<E> {
        if (bs.isEmpty() || other.isEmpty()) return this
        if (other === this) return emptyEnumSetOf()

        if (other is EnumEntriesBasedLargeEnumSet<*>) {
            if (!sameUniverse(values, other.values)) return this
            val otherWords = other.bs
            val resultWords = bs.copyOf()
            val minSize = minOf(resultWords.size, otherWords.size)
            var changed = false
            var lastNonZeroWordIndex = -1
            var wordIndex = 0
            while (wordIndex < minSize) {
                val oldWord = resultWords[wordIndex]
                val newWord = oldWord and otherWords[wordIndex].inv()
                if (newWord != oldWord) {
                    resultWords[wordIndex] = newWord
                    changed = true
                }
                if (resultWords[wordIndex] != 0L) lastNonZeroWordIndex = wordIndex
                wordIndex++
            }
            while (wordIndex < resultWords.size) {
                if (resultWords[wordIndex] != 0L) lastNonZeroWordIndex = wordIndex
                wordIndex++
            }
            if (!changed) return this
            return createLargeEnumSet(trimByLastNonZero(resultWords, lastNonZeroWordIndex), values)
        }

        val resultWords = bs.copyOf()
        var removed = false
        for (element in other) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal < 0) continue
            val wordIndex = ordinal ushr 6
            if (wordIndex >= resultWords.size) continue
            val oldWord = resultWords[wordIndex]
            val newWord = oldWord and (1L shl (ordinal and 63)).inv()
            if (newWord != oldWord) {
                removed = true
                resultWords[wordIndex] = newWord
            }
        }

        if (!removed) return this
        return createLargeEnumSet(trimByLastNonZero(resultWords, lastNonZeroIndex(resultWords)), values)
    }

    override fun isEmpty(): Boolean = bs.isEmpty()

    override fun iterator(): Iterator<E> = object : Iterator<E> {
        private var currentWordIndex = 0
        private var currentWord = if (bs.isNotEmpty()) bs[0] else 0L

        override fun hasNext(): Boolean {
            while (currentWord == 0L && currentWordIndex < bs.lastIndex) {
                currentWordIndex++
                currentWord = bs[currentWordIndex]
            }
            return currentWord != 0L
        }

        override fun next(): E {
            if (!hasNext()) throw NoSuchElementException()
            val bit = currentWord.countTrailingZeroBits()
            currentWord = currentWord and (currentWord - 1)
            return values[(currentWordIndex shl 6) + bit]
        }
    }

    override val size: Int
        get() = bitCountOf(bs)

    override fun toString(): String = joinToString(", ", "[", "]")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
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

    override fun hashCode(): Int {
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
}
