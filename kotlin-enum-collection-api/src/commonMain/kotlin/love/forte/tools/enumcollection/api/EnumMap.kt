package love.forte.tools.enumcollection.api

import kotlin.collections.AbstractMap
import kotlin.enums.EnumEntries
import kotlin.enums.enumEntries

/**
 * A map specialized for enum keys.
 *
 * Implementations use ordinal-indexed storage to reduce lookup overhead and object allocation.
 *
 * Example:
 * ```kotlin
 * enum class State { INIT, RUNNING }
 *
 * val map = enumMapOf(State.INIT to 0)
 * val code = map[State.INIT] // 0
 * ```
 */
public interface EnumMap<E : Enum<E>, V> : Map<E, V>

/**
 * Returns an immutable empty [EnumMap].
 *
 * Example: `emptyEnumMapOf<State, Int>().isEmpty()`
 */
@Suppress("UNCHECKED_CAST")
public fun <E : Enum<E>, V> emptyEnumMapOf(): EnumMap<E, V> = EmptyEnumMap as EnumMap<E, V>

/**
 * Converts this map to an immutable [EnumMap].
 *
 * For internal enum-map implementations, optimized snapshot paths are used.
 *
 * Example:
 * ```kotlin
 * val mutable = mutableEnumMapOf(State.INIT to 0)
 * val snapshot = mutable.toEnumMap()
 * mutable[State.RUNNING] = 1
 * // snapshot does not contain RUNNING
 * ```
 */
public fun <E : Enum<E>, V> Map<E, V>.toEnumMap(): EnumMap<E, V> {
    if (this is EnumMap<E, V> && this !is MutableEnumMap<E, V>) return this

    @Suppress("UNCHECKED_CAST")
    return when (this) {
        is EnumEntriesBasedI32EnumMap<*, *> -> {
            val map = this as EnumEntriesBasedI32EnumMap<E, V>
            val slotSize = mapHighestOrdinalPlusOne(map.keyBits)
            createI32EnumMap(map.keyBits, map.slots.copyOf(slotSize), map.universe)
        }

        is EnumEntriesBasedI64EnumMap<*, *> -> {
            val map = this as EnumEntriesBasedI64EnumMap<E, V>
            val slotSize = mapHighestOrdinalPlusOne(map.keyBits)
            createI64EnumMap(map.keyBits, map.slots.copyOf(slotSize), map.universe)
        }

        is EnumEntriesBasedLargeEnumMap<*, *> -> {
            val map = this as EnumEntriesBasedLargeEnumMap<E, V>
            val lastWordIndex = mapLastNonZeroWordIndex(map.keyWords)
            if (lastWordIndex < 0) {
                emptyEnumMapOf()
            } else {
                val keyWords = map.keyWords.copyOf(lastWordIndex + 1)
                val slotSize = mapHighestOrdinalPlusOne(keyWords)
                createLargeEnumMap(keyWords, map.slots.copyOf(slotSize), map.universe)
            }
        }

        else -> {
            if (isEmpty()) {
                emptyEnumMapOf()
            } else {
                GenericEnumMap(toMap())
            }
        }
    }
}

/**
 * Creates an immutable [EnumMap] from [pairs].
 *
 * Example: `enumMapOf(State.INIT to 0, State.RUNNING to 1)`
 */
public inline fun <reified E : Enum<E>, V> enumMapOf(vararg pairs: Pair<E, V>): EnumMap<E, V> {
    if (pairs.isEmpty()) return emptyEnumMapOf()

    val universe = enumEntries<E>()
    val universeSize = universe.size
    if (universeSize == 0) return emptyEnumMapOf()

    return when {
        universeSize <= Int.SIZE_BITS -> {
            var bits = 0
            var maxOrdinal = -1
            for ((key) in pairs) {
                val ordinal = key.ordinal
                bits = bits or (1 shl ordinal)
                if (ordinal > maxOrdinal) {
                    maxOrdinal = ordinal
                }
            }

            val slots = arrayOfNulls<Any?>(maxOrdinal + 1)
            for ((key, value) in pairs) {
                slots[key.ordinal] = value
            }
            createI32EnumMap(bits, slots, universe)
        }

        universeSize <= Long.SIZE_BITS -> {
            var bits = 0L
            var maxOrdinal = -1
            for ((key) in pairs) {
                val ordinal = key.ordinal
                bits = bits or (1L shl ordinal)
                if (ordinal > maxOrdinal) {
                    maxOrdinal = ordinal
                }
            }

            val slots = arrayOfNulls<Any?>(maxOrdinal + 1)
            for ((key, value) in pairs) {
                slots[key.ordinal] = value
            }
            createI64EnumMap(bits, slots, universe)
        }

        else -> {
            var maxOrdinal = -1
            for ((key) in pairs) {
                val ordinal = key.ordinal
                if (ordinal > maxOrdinal) maxOrdinal = ordinal
            }

            val words = LongArray((maxOrdinal + 64) ushr 6)
            val slots = arrayOfNulls<Any?>(maxOrdinal + 1)
            for ((key, value) in pairs) {
                val ordinal = key.ordinal
                val wordIndex = ordinal ushr 6
                words[wordIndex] = words[wordIndex] or (1L shl (ordinal and 63))
                slots[ordinal] = value
            }

            createLargeEnumMap(words, slots, universe)
        }
    }
}

@PublishedApi
internal fun <E : Enum<E>, V> createI32EnumMap(
    keyBits: Int,
    slots: Array<Any?>,
    universe: EnumEntries<E>
): EnumMap<E, V> {
    if (keyBits == 0) return emptyEnumMapOf()
    val requiredSlotSize = mapHighestOrdinalPlusOne(keyBits)
    val normalizedSlots = if (slots.size == requiredSlotSize) slots else slots.copyOf(requiredSlotSize)
    return I32EnumMap(universe, keyBits, normalizedSlots)
}

@PublishedApi
internal fun <E : Enum<E>, V> createI64EnumMap(
    keyBits: Long,
    slots: Array<Any?>,
    universe: EnumEntries<E>
): EnumMap<E, V> {
    if (keyBits == 0L) return emptyEnumMapOf()
    val requiredSlotSize = mapHighestOrdinalPlusOne(keyBits)
    val normalizedSlots = if (slots.size == requiredSlotSize) slots else slots.copyOf(requiredSlotSize)
    return I64EnumMap(universe, keyBits, normalizedSlots)
}

@PublishedApi
internal fun <E : Enum<E>, V> createLargeEnumMap(
    keyWords: LongArray,
    slots: Array<Any?>,
    universe: EnumEntries<E>
): EnumMap<E, V> {
    val lastWordIndex = mapLastNonZeroWordIndex(keyWords)
    if (lastWordIndex < 0) return emptyEnumMapOf()

    val trimmedWords = if (lastWordIndex == keyWords.lastIndex) keyWords else keyWords.copyOf(lastWordIndex + 1)
    val requiredSlotSize = mapHighestOrdinalPlusOne(trimmedWords)
    val normalizedSlots = if (slots.size == requiredSlotSize) slots else slots.copyOf(requiredSlotSize)
    return LargeEnumMap(universe, trimmedWords, normalizedSlots)
}

internal fun mapLastNonZeroWordIndex(words: LongArray): Int {
    var index = words.size - 1
    while (index >= 0 && words[index] == 0L) {
        index--
    }
    return index
}

internal fun mapHighestOrdinalPlusOne(words: LongArray): Int {
    if (words.isEmpty()) return 0
    val lastWordIndex = words.lastIndex
    val lastWord = words[lastWordIndex]
    if (lastWord == 0L) return 0
    val highestBit = 63 - lastWord.countLeadingZeroBits()
    return (lastWordIndex shl 6) + highestBit + 1
}

internal fun mapHighestOrdinalPlusOne(bits: Int): Int {
    if (bits == 0) return 0
    return Int.SIZE_BITS - bits.countLeadingZeroBits()
}

internal fun mapHighestOrdinalPlusOne(bits: Long): Int {
    if (bits == 0L) return 0
    return Long.SIZE_BITS - bits.countLeadingZeroBits()
}

internal fun mapBitCountOfWords(words: LongArray): Int {
    var count = 0
    for (word in words) {
        count += word.countOneBits()
    }
    return count
}

internal interface EnumEntriesBasedEnumMap<E : Enum<E>, V> : EnumMap<E, V> {
    val universe: EnumEntries<E>
}

internal interface EnumEntriesBasedI32EnumMap<E : Enum<E>, V> : EnumEntriesBasedEnumMap<E, V> {
    val keyBits: Int
    val slots: Array<Any?>
}

internal interface EnumEntriesBasedI64EnumMap<E : Enum<E>, V> : EnumEntriesBasedEnumMap<E, V> {
    val keyBits: Long
    val slots: Array<Any?>
}

internal interface EnumEntriesBasedLargeEnumMap<E : Enum<E>, V> : EnumEntriesBasedEnumMap<E, V> {
    val keyWords: LongArray
    val slots: Array<Any?>
}

private object EmptyEnumMap : EnumMap<Nothing, Nothing> {
    override val entries: Set<Map.Entry<Nothing, Nothing>> get() = emptySet()
    override val keys: Set<Nothing> get() = emptySet()
    override val values: Collection<Nothing> get() = emptyList()

    override val size: Int get() = 0
    override fun isEmpty(): Boolean = true
    override fun containsKey(key: Nothing): Boolean = false
    override fun containsValue(value: Nothing): Boolean = false
    override fun get(key: Nothing): Nothing? = null

}

internal class GenericEnumMap<E : Enum<E>, V>(private val delegate: Map<E, V>) :
    EnumMap<E, V>, Map<E, V> by delegate {
    override fun equals(other: Any?): Boolean = delegate == other

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = delegate.toString()
}

private class I32EnumMap<E : Enum<E>, V>(
    override val universe: EnumEntries<E>,
    override val keyBits: Int,
    override val slots: Array<Any?>
) : AbstractMap<E, V>(), EnumEntriesBasedI32EnumMap<E, V> {

    private fun keyOrdinalOrMinusOne(key: E): Int {
        val ordinal = key.ordinal
        return if (ordinal < universe.size) ordinal else -1
    }

    private fun containsOrdinal(ordinal: Int): Boolean = (keyBits and (1 shl ordinal)) != 0

    @Suppress("UNCHECKED_CAST")
    private fun valueAt(ordinal: Int): V = slots[ordinal] as V

    override val size: Int
        get() = keyBits.countOneBits()

    override fun isEmpty(): Boolean = keyBits == 0

    override fun containsKey(key: E): Boolean {
        val ordinal = keyOrdinalOrMinusOne(key)
        return ordinal >= 0 && containsOrdinal(ordinal)
    }

    override fun containsValue(value: V): Boolean {
        var remaining = keyBits
        while (remaining != 0) {
            val ordinal = remaining.countTrailingZeroBits()
            if (slots[ordinal] == value) return true
            remaining = remaining and (remaining - 1)
        }
        return false
    }

    override fun get(key: E): V? {
        val ordinal = keyOrdinalOrMinusOne(key)
        if (ordinal < 0 || !containsOrdinal(ordinal)) return null
        return valueAt(ordinal)
    }

    private var cachedEntries: Set<Map.Entry<E, V>>? = null

    override val entries: Set<Map.Entry<E, V>>
        get() {
            val cached = cachedEntries
            if (cached != null) return cached
            val created = createEntries()
            cachedEntries = created
            return created
        }

    private fun createEntries(): Set<Map.Entry<E, V>> = object : AbstractSet<Map.Entry<E, V>>() {
        override val size: Int
            get() = this@I32EnumMap.size

        override fun iterator(): Iterator<Map.Entry<E, V>> = EntryIterator()

        override fun contains(element: Map.Entry<E, V>): Boolean {
            val ordinal = keyOrdinalOrMinusOne(element.key)
            if (ordinal < 0 || !containsOrdinal(ordinal)) return false
            return slots[ordinal] == element.value
        }
    }

    private inner class EntryIterator : Iterator<Map.Entry<E, V>> {
        private var remaining = keyBits

        override fun hasNext(): Boolean = remaining != 0

        override fun next(): Map.Entry<E, V> {
            val current = remaining
            if (current == 0) throw NoSuchElementException()
            val ordinal = current.countTrailingZeroBits()
            remaining = current and (current - 1)
            return EntryImpl(ordinal)
        }
    }

    private inner class EntryImpl(private val ordinal: Int) : Map.Entry<E, V> {
        override val key: E
            get() = universe[ordinal]

        override val value: V
            get() = valueAt(ordinal)

        override fun equals(other: Any?): Boolean {
            if (other !is Map.Entry<*, *>) return false
            return key == other.key && value == other.value
        }

        override fun hashCode(): Int = key.hashCode() xor (value?.hashCode() ?: 0)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Map<*, *>) return false
        if (size != other.size) return false

        if (other is EnumEntriesBasedI32EnumMap<*, *>) {
            if (!sameUniverse(universe, other.universe)) {
                return isEmpty() && other.isEmpty()
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

        for ((key, value) in entries) {
            val otherValue = other[key]
            if (otherValue != value || (value == null && !other.containsKey(key))) return false
        }
        return true
    }

    override fun hashCode(): Int {
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
}

private class I64EnumMap<E : Enum<E>, V>(
    override val universe: EnumEntries<E>,
    override val keyBits: Long,
    override val slots: Array<Any?>
) : AbstractMap<E, V>(), EnumEntriesBasedI64EnumMap<E, V> {

    private fun keyOrdinalOrMinusOne(key: E): Int {
        val ordinal = key.ordinal
        return if (ordinal < universe.size) ordinal else -1
    }

    private fun containsOrdinal(ordinal: Int): Boolean = (keyBits and (1L shl ordinal)) != 0L

    @Suppress("UNCHECKED_CAST")
    private fun valueAt(ordinal: Int): V = slots[ordinal] as V

    override val size: Int
        get() = keyBits.countOneBits()

    override fun isEmpty(): Boolean = keyBits == 0L

    override fun containsKey(key: E): Boolean {
        val ordinal = keyOrdinalOrMinusOne(key)
        return ordinal >= 0 && containsOrdinal(ordinal)
    }

    override fun containsValue(value: V): Boolean {
        var remaining = keyBits
        while (remaining != 0L) {
            val ordinal = remaining.countTrailingZeroBits()
            if (slots[ordinal] == value) return true
            remaining = remaining and (remaining - 1)
        }
        return false
    }

    override fun get(key: E): V? {
        val ordinal = keyOrdinalOrMinusOne(key)
        if (ordinal < 0 || !containsOrdinal(ordinal)) return null
        return valueAt(ordinal)
    }

    private var cachedEntries: Set<Map.Entry<E, V>>? = null

    override val entries: Set<Map.Entry<E, V>>
        get() {
            val cached = cachedEntries
            if (cached != null) return cached
            val created = createEntries()
            cachedEntries = created
            return created
        }

    private fun createEntries(): Set<Map.Entry<E, V>> = object : AbstractSet<Map.Entry<E, V>>() {
        override val size: Int
            get() = this@I64EnumMap.size

        override fun iterator(): Iterator<Map.Entry<E, V>> = EntryIterator()

        override fun contains(element: Map.Entry<E, V>): Boolean {
            val ordinal = keyOrdinalOrMinusOne(element.key)
            if (ordinal < 0 || !containsOrdinal(ordinal)) return false
            return slots[ordinal] == element.value
        }
    }

    private inner class EntryIterator : Iterator<Map.Entry<E, V>> {
        private var remaining = keyBits

        override fun hasNext(): Boolean = remaining != 0L

        override fun next(): Map.Entry<E, V> {
            val current = remaining
            if (current == 0L) throw NoSuchElementException()
            val ordinal = current.countTrailingZeroBits()
            remaining = current and (current - 1)
            return EntryImpl(ordinal)
        }
    }

    private inner class EntryImpl(private val ordinal: Int) : Map.Entry<E, V> {
        override val key: E
            get() = universe[ordinal]

        override val value: V
            get() = valueAt(ordinal)

        override fun equals(other: Any?): Boolean {
            if (other !is Map.Entry<*, *>) return false
            return key == other.key && value == other.value
        }

        override fun hashCode(): Int = key.hashCode() xor (value?.hashCode() ?: 0)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Map<*, *>) return false
        if (size != other.size) return false

        if (other is EnumEntriesBasedI64EnumMap<*, *>) {
            if (!sameUniverse(universe, other.universe)) {
                return isEmpty() && other.isEmpty()
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

        for ((key, value) in entries) {
            val otherValue = other[key]
            if (otherValue != value || (value == null && !other.containsKey(key))) return false
        }
        return true
    }

    override fun hashCode(): Int {
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
}

private class LargeEnumMap<E : Enum<E>, V>(
    override val universe: EnumEntries<E>,
    override val keyWords: LongArray,
    override val slots: Array<Any?>
) : AbstractMap<E, V>(), EnumEntriesBasedLargeEnumMap<E, V> {

    private val mapSize: Int = mapBitCountOfWords(keyWords)

    private fun keyOrdinalOrMinusOne(key: E): Int {
        val ordinal = key.ordinal
        return if (ordinal < universe.size) ordinal else -1
    }

    private fun containsOrdinal(ordinal: Int): Boolean {
        val wordIndex = ordinal ushr 6
        if (wordIndex >= keyWords.size) return false
        return (keyWords[wordIndex] and (1L shl (ordinal and 63))) != 0L
    }

    @Suppress("UNCHECKED_CAST")
    private fun valueAt(ordinal: Int): V = slots[ordinal] as V

    override val size: Int
        get() = mapSize

    override fun isEmpty(): Boolean = mapSize == 0

    override fun containsKey(key: E): Boolean {
        val ordinal = keyOrdinalOrMinusOne(key)
        return ordinal >= 0 && containsOrdinal(ordinal)
    }

    override fun containsValue(value: V): Boolean {
        for (wordIndex in keyWords.indices) {
            var word = keyWords[wordIndex]
            while (word != 0L) {
                val bit = word.countTrailingZeroBits()
                val ordinal = (wordIndex shl 6) + bit
                if (ordinal < slots.size && slots[ordinal] == value) return true
                word = word and (word - 1)
            }
        }
        return false
    }

    override fun get(key: E): V? {
        val ordinal = keyOrdinalOrMinusOne(key)
        if (ordinal < 0 || !containsOrdinal(ordinal) || ordinal >= slots.size) return null
        return valueAt(ordinal)
    }

    private var cachedEntries: Set<Map.Entry<E, V>>? = null

    override val entries: Set<Map.Entry<E, V>>
        get() {
            val cached = cachedEntries
            if (cached != null) return cached
            val created = createEntries()
            cachedEntries = created
            return created
        }

    private fun createEntries(): Set<Map.Entry<E, V>> = object : AbstractSet<Map.Entry<E, V>>() {
        override val size: Int
            get() = mapSize

        override fun iterator(): Iterator<Map.Entry<E, V>> = EntryIterator()

        override fun contains(element: Map.Entry<E, V>): Boolean {
            val ordinal = keyOrdinalOrMinusOne(element.key)
            if (ordinal < 0 || !containsOrdinal(ordinal) || ordinal >= slots.size) return false
            return slots[ordinal] == element.value
        }
    }

    private inner class EntryIterator : Iterator<Map.Entry<E, V>> {
        private var currentWordIndex = 0
        private var currentWord = if (keyWords.isNotEmpty()) keyWords[0] else 0L

        override fun hasNext(): Boolean {
            while (currentWord == 0L && currentWordIndex < keyWords.lastIndex) {
                currentWordIndex++
                currentWord = keyWords[currentWordIndex]
            }
            return currentWord != 0L
        }

        override fun next(): Map.Entry<E, V> {
            if (!hasNext()) throw NoSuchElementException()
            val bit = currentWord.countTrailingZeroBits()
            val ordinal = (currentWordIndex shl 6) + bit
            currentWord = currentWord and (currentWord - 1)
            return EntryImpl(ordinal)
        }
    }

    private inner class EntryImpl(private val ordinal: Int) : Map.Entry<E, V> {
        override val key: E
            get() = universe[ordinal]

        override val value: V
            get() = valueAt(ordinal)

        override fun equals(other: Any?): Boolean {
            if (other !is Map.Entry<*, *>) return false
            return key == other.key && value == other.value
        }

        override fun hashCode(): Int = key.hashCode() xor (value?.hashCode() ?: 0)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Map<*, *>) return false
        if (size != other.size) return false

        if (other is EnumEntriesBasedLargeEnumMap<*, *>) {
            if (!sameUniverse(universe, other.universe)) {
                return isEmpty() && other.isEmpty()
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

        for ((key, value) in entries) {
            val otherValue = other[key]
            if (otherValue != value || (value == null && !other.containsKey(key))) return false
        }
        return true
    }

    override fun hashCode(): Int {
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
}
