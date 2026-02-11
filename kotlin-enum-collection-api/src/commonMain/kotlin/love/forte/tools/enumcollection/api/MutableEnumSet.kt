package love.forte.tools.enumcollection.api

import kotlin.enums.EnumEntries
import kotlin.enums.enumEntries

/**
 * A mutable [EnumSet].
 */
public interface MutableEnumSet<E : Enum<E>> : EnumSet<E>, MutableSet<E> {
    /**
     * Returns an independent mutable copy of this set.
     *
     * Example:
     * ```kotlin
     * val original = mutableEnumSetOf(Role.USER)
     * val copy = original.copy()
     * copy.add(Role.ADMIN)
     * ```
     */
    public fun copy(): MutableEnumSet<E>
}

/**
 * Converts this set to a mutable [MutableEnumSet].
 *
 * Similar to Kotlin's `toMutableSet`, this creates an independent mutable copy.
 *
 * Example: `val mutable = enumSetOf(Role.USER).toMutableEnumSet()`
 */
public fun <E : Enum<E>> Set<E>.toMutableEnumSet(): MutableEnumSet<E> {
    if (this is MutableEnumSet<E>) return copy()

    @Suppress("UNCHECKED_CAST")
    return when (this) {
        is EnumEntriesBasedI32EnumSet<*> -> {
            val set = this as EnumEntriesBasedI32EnumSet<E>
            createMutableI32EnumSet(set.bs, set.values)
        }

        is EnumEntriesBasedI64EnumSet<*> -> {
            val set = this as EnumEntriesBasedI64EnumSet<E>
            createMutableI64EnumSet(set.bs, set.values)
        }

        is EnumEntriesBasedLargeEnumSet<*> -> {
            val set = this as EnumEntriesBasedLargeEnumSet<E>
            createMutableLargeEnumSet(set.bs.copyOf(), set.values)
        }

        else -> GenericMutableEnumSet(toMutableSet())
    }
}

/**
 * Creates a mutable [MutableEnumSet] containing [elements].
 *
 * Example: `mutableEnumSetOf(Role.USER, Role.ADMIN)`
 */
public inline fun <reified E : Enum<E>> mutableEnumSetOf(vararg elements: E): MutableEnumSet<E> {
    val entries = enumEntries<E>()
    val entrySize = entries.size
    return when {
        entrySize <= Int.SIZE_BITS -> createMutableI32EnumSet(calculateI32BitSize(elements), entries)
        entrySize <= Long.SIZE_BITS -> createMutableI64EnumSet(calculateI64BitSize(elements), entries)
        else -> createMutableLargeEnumSet(calculateLargeBitSize(entrySize, elements), entries)
    }
}

@PublishedApi
internal fun <E : Enum<E>> createMutableI32EnumSet(bs: Int, values: EnumEntries<E>): MutableEnumSet<E> =
    MutableI32EnumSet(bs, values)

@PublishedApi
internal fun <E : Enum<E>> createMutableI64EnumSet(bs: Long, values: EnumEntries<E>): MutableEnumSet<E> =
    MutableI64EnumSet(bs, values)

@PublishedApi
internal fun <E : Enum<E>> createMutableLargeEnumSet(bs: LongArray, values: EnumEntries<E>): MutableEnumSet<E> {
    val lastNonZeroWordIndex = lastNonZeroIndex(bs)
    val words = trimByLastNonZero(bs, lastNonZeroWordIndex)
    return MutableLargeEnumSet(words, values)
}

internal interface EnumEntriesBasedI32MutableEnumSet<E : Enum<E>> :
    MutableEnumSet<E>, EnumEntriesBasedI32EnumSet<E> {
    override var bs: Int
}

internal interface EnumEntriesBasedI64MutableEnumSet<E : Enum<E>> :
    MutableEnumSet<E>, EnumEntriesBasedI64EnumSet<E> {
    override var bs: Long
}

internal interface EnumEntriesBasedLargeMutableEnumSet<E : Enum<E>> :
    MutableEnumSet<E>, EnumEntriesBasedLargeEnumSet<E> {
    override var bs: LongArray
}

private class GenericMutableEnumSet<E : Enum<E>>(private val delegate: MutableSet<E>) :
    MutableEnumSet<E>, MutableSet<E> by delegate {

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
        if (other === this) return GenericEnumSet(delegate.toSet())

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
        if (other.isEmpty()) return GenericEnumSet(delegate.toSet())
        if (other === this) return GenericEnumSet(delegate.toSet())

        val result = LinkedHashSet<E>(delegate.size + other.size)
        result.addAll(delegate)
        result.addAll(other)
        return GenericEnumSet(result)
    }

    override fun difference(other: Set<E>): Set<E> {
        if (delegate.isEmpty()) return emptyEnumSetOf()
        if (other.isEmpty()) return GenericEnumSet(delegate.toSet())
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

    override fun copy(): MutableEnumSet<E> = GenericMutableEnumSet(delegate.toMutableSet())

    override fun equals(other: Any?): Boolean = delegate == other

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = delegate.toString()
}

private class MutableI32EnumSet<E : Enum<E>>(
    override var bs: Int = 0,
    override val values: EnumEntries<E>
) : EnumEntriesBasedI32MutableEnumSet<E> {
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
        if (other === this) return createI32EnumSet(currentBits, values)

        if (other is EnumEntriesBasedI32EnumSet<*>) {
            if (!sameUniverse(values, other.values)) return emptyEnumSetOf()
            return createI32EnumSet(currentBits and other.bs, values)
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

        return createI32EnumSet(intersectBits, values)
    }

    override fun union(other: Set<E>): Set<E> {
        val currentBits = bs
        if (other.isEmpty()) return createI32EnumSet(currentBits, values)
        if (other === this) return createI32EnumSet(currentBits, values)

        if (other is EnumEntriesBasedI32EnumSet<*>) {
            if (!sameUniverse(values, other.values)) return createI32EnumSet(currentBits, values)
            return createI32EnumSet(currentBits or other.bs, values)
        }

        var mergedBits = currentBits
        for (element in other) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal >= 0) {
                mergedBits = mergedBits or (1 shl ordinal)
            }
        }

        return createI32EnumSet(mergedBits, values)
    }

    override fun difference(other: Set<E>): Set<E> {
        val currentBits = bs
        if (currentBits == 0 || other.isEmpty()) return createI32EnumSet(currentBits, values)
        if (other === this) return emptyEnumSetOf()

        if (other is EnumEntriesBasedI32EnumSet<*>) {
            if (!sameUniverse(values, other.values)) return createI32EnumSet(currentBits, values)
            return createI32EnumSet(currentBits and other.bs.inv(), values)
        }

        var removeMask = 0
        for (element in other) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal >= 0) {
                removeMask = removeMask or (1 shl ordinal)
            }
        }

        return createI32EnumSet(currentBits and removeMask.inv(), values)
    }

    override fun copy(): MutableEnumSet<E> = MutableI32EnumSet(bs, values)

    override fun isEmpty(): Boolean = bs == 0

    override fun iterator(): MutableIterator<E> = object : MutableIterator<E> {
        private var remaining = bs
        private var lastBit = -1

        override fun hasNext(): Boolean = remaining != 0

        override fun next(): E {
            val current = remaining
            if (current == 0) throw NoSuchElementException()
            val bit = current.countTrailingZeroBits()
            lastBit = bit
            remaining = current and (current - 1)
            return values[bit]
        }

        override fun remove() {
            check(lastBit >= 0) { "next() must be called before remove()" }
            bs = bs and (1 shl lastBit).inv()
            lastBit = -1
        }
    }

    override val size: Int
        get() = bs.countOneBits()

    override fun add(element: E): Boolean {
        val oldBits = bs
        bs = oldBits or (1 shl element.ordinal)
        return bs != oldBits
    }

    override fun addAll(elements: Collection<E>): Boolean {
        if (elements.isEmpty()) return false

        if (elements is EnumEntriesBasedI32EnumSet<*>) {
            if (!sameUniverse(values, elements.values)) return false
            val oldBits = bs
            bs = oldBits or elements.bs
            return bs != oldBits
        }

        val oldBits = bs
        var mergedBits = oldBits
        for (element in elements) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal >= 0) {
                mergedBits = mergedBits or (1 shl ordinal)
            }
        }
        bs = mergedBits

        return bs != oldBits
    }

    override fun clear() {
        bs = 0
    }

    override fun remove(element: E): Boolean {
        val oldBits = bs
        bs = oldBits and (1 shl element.ordinal).inv()
        return bs != oldBits
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        if (elements.isEmpty() || bs == 0) return false

        if (elements is EnumEntriesBasedI32EnumSet<*>) {
            if (!sameUniverse(values, elements.values)) return false
            val oldBits = bs
            bs = oldBits and elements.bs.inv()
            return bs != oldBits
        }

        var removeMask = 0
        for (element in elements) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal >= 0) {
                removeMask = removeMask or (1 shl ordinal)
            }
        }

        if (removeMask == 0) return false

        val oldBits = bs
        bs = oldBits and removeMask.inv()
        return bs != oldBits
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        val currentBits = bs
        if (currentBits == 0) return false

        if (elements is EnumEntriesBasedI32EnumSet<*>) {
            if (!sameUniverse(values, elements.values)) {
                bs = 0
                return true
            }
            val retainedBits = currentBits and elements.bs
            if (retainedBits == currentBits) return false
            bs = retainedBits
            return true
        }

        if (elements.isEmpty()) {
            bs = 0
            return true
        }

        var retainedBits = 0
        if (elements is Set<*> && elements.size > currentBits.countOneBits()) {
            var remaining = currentBits
            while (remaining != 0) {
                val bit = remaining.countTrailingZeroBits()
                if (elements.contains(values[bit])) {
                    retainedBits = retainedBits or (1 shl bit)
                }
                remaining = remaining and (remaining - 1)
            }
        } else {
            for (element in elements) {
                val ordinal = ordinalInUniverseOrMinusOne(element, values)
                if (ordinal < 0) continue
                val mask = 1 shl ordinal
                retainedBits = retainedBits or (currentBits and mask)
            }
        }

        if (retainedBits == currentBits) return false
        bs = retainedBits
        return true
    }

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

private class MutableI64EnumSet<E : Enum<E>>(
    override var bs: Long = 0L,
    override val values: EnumEntries<E>
) : EnumEntriesBasedI64MutableEnumSet<E> {
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
        if (other === this) return createI64EnumSet(currentBits, values)

        if (other is EnumEntriesBasedI64EnumSet<*>) {
            if (!sameUniverse(values, other.values)) return emptyEnumSetOf()
            return createI64EnumSet(currentBits and other.bs, values)
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

        return createI64EnumSet(intersectBits, values)
    }

    override fun union(other: Set<E>): Set<E> {
        val currentBits = bs
        if (other.isEmpty()) return createI64EnumSet(currentBits, values)
        if (other === this) return createI64EnumSet(currentBits, values)

        if (other is EnumEntriesBasedI64EnumSet<*>) {
            if (!sameUniverse(values, other.values)) return createI64EnumSet(currentBits, values)
            return createI64EnumSet(currentBits or other.bs, values)
        }

        var mergedBits = currentBits
        for (element in other) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal >= 0) {
                mergedBits = mergedBits or (1L shl ordinal)
            }
        }

        return createI64EnumSet(mergedBits, values)
    }

    override fun difference(other: Set<E>): Set<E> {
        val currentBits = bs
        if (currentBits == 0L || other.isEmpty()) return createI64EnumSet(currentBits, values)
        if (other === this) return emptyEnumSetOf()

        if (other is EnumEntriesBasedI64EnumSet<*>) {
            if (!sameUniverse(values, other.values)) return createI64EnumSet(currentBits, values)
            return createI64EnumSet(currentBits and other.bs.inv(), values)
        }

        var removeMask = 0L
        for (element in other) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal >= 0) {
                removeMask = removeMask or (1L shl ordinal)
            }
        }

        return createI64EnumSet(currentBits and removeMask.inv(), values)
    }

    override fun copy(): MutableEnumSet<E> = MutableI64EnumSet(bs, values)

    override fun isEmpty(): Boolean = bs == 0L

    override fun iterator(): MutableIterator<E> = object : MutableIterator<E> {
        private var remaining = bs
        private var lastBit = -1

        override fun hasNext(): Boolean = remaining != 0L

        override fun next(): E {
            val current = remaining
            if (current == 0L) throw NoSuchElementException()
            val bit = current.countTrailingZeroBits()
            lastBit = bit
            remaining = current and (current - 1)
            return values[bit]
        }

        override fun remove() {
            check(lastBit >= 0) { "next() must be called before remove()" }
            bs = bs and (1L shl lastBit).inv()
            lastBit = -1
        }
    }

    override val size: Int
        get() = bs.countOneBits()

    override fun add(element: E): Boolean {
        val oldBits = bs
        bs = oldBits or (1L shl element.ordinal)
        return bs != oldBits
    }

    override fun addAll(elements: Collection<E>): Boolean {
        if (elements.isEmpty()) return false

        if (elements is EnumEntriesBasedI64EnumSet<*>) {
            if (!sameUniverse(values, elements.values)) return false
            val oldBits = bs
            bs = oldBits or elements.bs
            return bs != oldBits
        }

        val oldBits = bs
        var mergedBits = oldBits
        for (element in elements) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal >= 0) {
                mergedBits = mergedBits or (1L shl ordinal)
            }
        }
        bs = mergedBits

        return bs != oldBits
    }

    override fun clear() {
        bs = 0L
    }

    override fun remove(element: E): Boolean {
        val oldBits = bs
        bs = oldBits and (1L shl element.ordinal).inv()
        return bs != oldBits
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        if (elements.isEmpty() || bs == 0L) return false

        if (elements is EnumEntriesBasedI64EnumSet<*>) {
            if (!sameUniverse(values, elements.values)) return false
            val oldBits = bs
            bs = oldBits and elements.bs.inv()
            return bs != oldBits
        }

        var removeMask = 0L
        for (element in elements) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal >= 0) {
                removeMask = removeMask or (1L shl ordinal)
            }
        }

        if (removeMask == 0L) return false

        val oldBits = bs
        bs = oldBits and removeMask.inv()
        return bs != oldBits
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        val currentBits = bs
        if (currentBits == 0L) return false

        if (elements is EnumEntriesBasedI64EnumSet<*>) {
            if (!sameUniverse(values, elements.values)) {
                bs = 0L
                return true
            }
            val retainedBits = currentBits and elements.bs
            if (retainedBits == currentBits) return false
            bs = retainedBits
            return true
        }

        if (elements.isEmpty()) {
            bs = 0L
            return true
        }

        var retainedBits = 0L
        if (elements is Set<*> && elements.size > currentBits.countOneBits()) {
            var remaining = currentBits
            while (remaining != 0L) {
                val bit = remaining.countTrailingZeroBits()
                if (elements.contains(values[bit])) {
                    retainedBits = retainedBits or (1L shl bit)
                }
                remaining = remaining and (remaining - 1)
            }
        } else {
            for (element in elements) {
                val ordinal = ordinalInUniverseOrMinusOne(element, values)
                if (ordinal < 0) continue
                val mask = 1L shl ordinal
                retainedBits = retainedBits or (currentBits and mask)
            }
        }

        if (retainedBits == currentBits) return false
        bs = retainedBits
        return true
    }

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

private class MutableLargeEnumSet<E : Enum<E>>(
    override var bs: LongArray = longArrayOf(),
    override val values: EnumEntries<E>
) : EnumEntriesBasedLargeMutableEnumSet<E> {
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
        if (other === this) return createLargeEnumSet(bs.copyOf(), values)

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
        if (other.isEmpty()) return createLargeEnumSet(bs.copyOf(), values)
        if (other === this) return createLargeEnumSet(bs.copyOf(), values)

        if (other is EnumEntriesBasedLargeEnumSet<*>) {
            if (!sameUniverse(values, other.values)) return createLargeEnumSet(bs.copyOf(), values)
            val otherWords = other.bs
            val maxSize = maxOf(bs.size, otherWords.size)
            val resultWords = LongArray(maxSize)
            for (wordIndex in 0 until maxSize) {
                val leftWord = if (wordIndex < bs.size) bs[wordIndex] else 0L
                val rightWord = if (wordIndex < otherWords.size) otherWords[wordIndex] else 0L
                resultWords[wordIndex] = leftWord or rightWord
            }
            return createLargeEnumSet(resultWords, values)
        }

        var resultWords = bs.copyOf()
        for (element in other) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal < 0) continue
            val wordIndex = ordinal ushr 6
            if (wordIndex >= resultWords.size) {
                resultWords = resultWords.copyOf(wordIndex + 1)
            }
            resultWords[wordIndex] = resultWords[wordIndex] or (1L shl (ordinal and 63))
        }
        return createLargeEnumSet(resultWords, values)
    }

    override fun difference(other: Set<E>): Set<E> {
        if (bs.isEmpty() || other.isEmpty()) return createLargeEnumSet(bs.copyOf(), values)
        if (other === this) return emptyEnumSetOf()

        if (other is EnumEntriesBasedLargeEnumSet<*>) {
            if (!sameUniverse(values, other.values)) return createLargeEnumSet(bs.copyOf(), values)
            val otherWords = other.bs
            val resultWords = bs.copyOf()
            val minSize = minOf(resultWords.size, otherWords.size)
            var lastNonZeroWordIndex = -1
            var wordIndex = 0
            while (wordIndex < minSize) {
                resultWords[wordIndex] = resultWords[wordIndex] and otherWords[wordIndex].inv()
                if (resultWords[wordIndex] != 0L) lastNonZeroWordIndex = wordIndex
                wordIndex++
            }
            while (wordIndex < resultWords.size) {
                if (resultWords[wordIndex] != 0L) lastNonZeroWordIndex = wordIndex
                wordIndex++
            }
            return createLargeEnumSet(trimByLastNonZero(resultWords, lastNonZeroWordIndex), values)
        }

        val resultWords = bs.copyOf()
        var removeMaskExists = false
        for (element in other) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal < 0) continue
            val wordIndex = ordinal ushr 6
            if (wordIndex >= resultWords.size) continue
            val oldWord = resultWords[wordIndex]
            val newWord = oldWord and (1L shl (ordinal and 63)).inv()
            if (newWord != oldWord) {
                removeMaskExists = true
                resultWords[wordIndex] = newWord
            }
        }

        if (!removeMaskExists) return createLargeEnumSet(bs.copyOf(), values)
        return createLargeEnumSet(trimByLastNonZero(resultWords, lastNonZeroIndex(resultWords)), values)
    }

    override fun copy(): MutableEnumSet<E> = MutableLargeEnumSet(bs.copyOf(), values)

    override fun isEmpty(): Boolean = bs.isEmpty()

    override fun iterator(): MutableIterator<E> = object : MutableIterator<E> {
        private var currentWordIndex = 0
        private var currentWord = if (bs.isNotEmpty()) bs[0] else 0L
        private var lastReturnedWordIndex = -1
        private var lastReturnedBit = -1

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
            lastReturnedWordIndex = currentWordIndex
            lastReturnedBit = bit
            currentWord = currentWord and (currentWord - 1)
            return values[(currentWordIndex shl 6) + bit]
        }

        override fun remove() {
            check(lastReturnedWordIndex >= 0) { "next() must be called before remove()" }
            val wordIndex = lastReturnedWordIndex
            val newWord = bs[wordIndex] and (1L shl lastReturnedBit).inv()
            bs[wordIndex] = newWord
            if (wordIndex == bs.lastIndex && newWord == 0L) {
                bs = trimByLastNonZero(bs, lastNonZeroIndex(bs))
                if (currentWordIndex >= bs.size) {
                    currentWordIndex = bs.size
                    currentWord = 0L
                }
            }
            lastReturnedWordIndex = -1
            lastReturnedBit = -1
        }
    }

    override val size: Int
        get() = bitCountOf(bs)

    override fun add(element: E): Boolean {
        val ordinal = element.ordinal
        val wordIndex = ordinal ushr 6
        if (wordIndex >= bs.size) {
            bs = bs.copyOf(wordIndex + 1)
        }
        val oldWord = bs[wordIndex]
        val newWord = oldWord or (1L shl (ordinal and 63))
        bs[wordIndex] = newWord
        return newWord != oldWord
    }

    override fun addAll(elements: Collection<E>): Boolean {
        if (elements.isEmpty()) return false

        if (elements is EnumEntriesBasedLargeEnumSet<*>) {
            if (!sameUniverse(values, elements.values)) return false
            val otherWords = elements.bs
            if (otherWords.isEmpty()) return false
            if (otherWords.size > bs.size) {
                bs = bs.copyOf(otherWords.size)
            }
            var modified = false
            for (wordIndex in otherWords.indices) {
                val oldWord = bs[wordIndex]
                val newWord = oldWord or otherWords[wordIndex]
                if (newWord != oldWord) modified = true
                bs[wordIndex] = newWord
            }
            return modified
        }

        var modified = false
        for (element in elements) {
            if (add(element)) modified = true
        }
        return modified
    }

    override fun clear() {
        bs = LongArray(0)
    }

    override fun remove(element: E): Boolean {
        val ordinal = element.ordinal
        val wordIndex = ordinal ushr 6
        if (wordIndex >= bs.size) return false

        val oldWord = bs[wordIndex]
        val newWord = oldWord and (1L shl (ordinal and 63)).inv()
        if (newWord == oldWord) return false

        bs[wordIndex] = newWord
        if (wordIndex == bs.lastIndex && newWord == 0L) {
            bs = trimByLastNonZero(bs, lastNonZeroIndex(bs))
        }
        return true
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        if (elements.isEmpty() || bs.isEmpty()) return false

        if (elements is EnumEntriesBasedLargeEnumSet<*>) {
            if (!sameUniverse(values, elements.values)) return false
            val otherWords = elements.bs
            val minSize = minOf(bs.size, otherWords.size)
            var modified = false
            var lastNonZeroWordIndex = -1
            var wordIndex = 0
            while (wordIndex < minSize) {
                val oldWord = bs[wordIndex]
                val newWord = oldWord and otherWords[wordIndex].inv()
                if (newWord != oldWord) modified = true
                bs[wordIndex] = newWord
                if (newWord != 0L) lastNonZeroWordIndex = wordIndex
                wordIndex++
            }
            while (wordIndex < bs.size) {
                if (bs[wordIndex] != 0L) lastNonZeroWordIndex = wordIndex
                wordIndex++
            }
            if (!modified) return false
            bs = trimByLastNonZero(bs, lastNonZeroWordIndex)
            return true
        }

        val removeMaskWords = LongArray(bs.size)
        var removeMaskExists = false
        for (element in elements) {
            val ordinal = ordinalInUniverseOrMinusOne(element, values)
            if (ordinal < 0) continue
            val wordIndex = ordinal ushr 6
            if (wordIndex >= bs.size) continue
            removeMaskWords[wordIndex] = removeMaskWords[wordIndex] or (1L shl (ordinal and 63))
            removeMaskExists = true
        }

        if (!removeMaskExists) return false

        var modified = false
        var lastNonZeroWordIndex = -1
        for (wordIndex in bs.indices) {
            val oldWord = bs[wordIndex]
            val newWord = oldWord and removeMaskWords[wordIndex].inv()
            if (newWord != oldWord) modified = true
            bs[wordIndex] = newWord
            if (newWord != 0L) lastNonZeroWordIndex = wordIndex
        }

        if (!modified) return false
        bs = trimByLastNonZero(bs, lastNonZeroWordIndex)
        return true
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        if (bs.isEmpty()) return false

        if (elements is EnumEntriesBasedLargeEnumSet<*>) {
            if (!sameUniverse(values, elements.values)) {
                bs = LongArray(0)
                return true
            }
            val otherWords = elements.bs
            val minSize = minOf(bs.size, otherWords.size)
            var modified = false
            var lastNonZeroWordIndex = -1
            for (wordIndex in 0 until minSize) {
                val oldWord = bs[wordIndex]
                val newWord = oldWord and otherWords[wordIndex]
                if (newWord != oldWord) modified = true
                bs[wordIndex] = newWord
                if (newWord != 0L) lastNonZeroWordIndex = wordIndex
            }
            for (wordIndex in minSize until bs.size) {
                if (bs[wordIndex] != 0L) modified = true
                bs[wordIndex] = 0L
            }
            if (!modified) return false
            bs = trimByLastNonZero(bs, lastNonZeroWordIndex)
            return true
        }

        if (elements.isEmpty()) {
            bs = LongArray(0)
            return true
        }

        val currentWords = bs
        val retainedWords = LongArray(currentWords.size)
        var lastNonZeroWordIndex = -1
        if (elements is Set<*> && elements.size > size) {
            for (wordIndex in currentWords.indices) {
                var word = currentWords[wordIndex]
                while (word != 0L) {
                    val bit = word.countTrailingZeroBits()
                    if (elements.contains(values[(wordIndex shl 6) + bit])) {
                        retainedWords[wordIndex] = retainedWords[wordIndex] or (1L shl bit)
                        lastNonZeroWordIndex = wordIndex
                    }
                    word = word and (word - 1)
                }
            }
        } else {
            for (element in elements) {
                val ordinal = ordinalInUniverseOrMinusOne(element, values)
                if (ordinal < 0) continue
                val wordIndex = ordinal ushr 6
                if (wordIndex >= currentWords.size) continue
                val bitMask = 1L shl (ordinal and 63)
                if ((currentWords[wordIndex] and bitMask) == 0L) continue
                retainedWords[wordIndex] = retainedWords[wordIndex] or bitMask
                if (wordIndex > lastNonZeroWordIndex) lastNonZeroWordIndex = wordIndex
            }
        }

        var modified = currentWords.size != (lastNonZeroWordIndex + 1)
        if (!modified) {
            for (wordIndex in 0..lastNonZeroWordIndex) {
                if (retainedWords[wordIndex] != currentWords[wordIndex]) {
                    modified = true
                    break
                }
            }
        }

        if (!modified) return false
        bs = trimByLastNonZero(retainedWords, lastNonZeroWordIndex)
        return true
    }

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
