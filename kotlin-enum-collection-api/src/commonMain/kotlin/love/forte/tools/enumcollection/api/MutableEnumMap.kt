package love.forte.tools.enumcollection.api

import kotlin.collections.AbstractMutableMap
import kotlin.enums.EnumEntries
import kotlin.enums.enumEntries

/**
 * A mutable [EnumMap].
 */
public interface MutableEnumMap<E : Enum<E>, V> : EnumMap<E, V>, MutableMap<E, V>

/**
 * Converts this map to a mutable [MutableEnumMap].
 *
 * Similar to Kotlin's `toMutableMap`, this creates an independent mutable copy.
 */
public fun <E : Enum<E>, V> Map<E, V>.toMutableEnumMap(): MutableEnumMap<E, V> {
    @Suppress("UNCHECKED_CAST")
    return when (this) {
        is EnumEntriesBasedI32EnumMap<*, *> -> {
            val map = this as EnumEntriesBasedI32EnumMap<E, V>
            createMutableI32EnumMap(map.keyBits, map.slots, map.universe)
        }

        is EnumEntriesBasedI64EnumMap<*, *> -> {
            val map = this as EnumEntriesBasedI64EnumMap<E, V>
            createMutableI64EnumMap(map.keyBits, map.slots, map.universe)
        }

        is EnumEntriesBasedLargeEnumMap<*, *> -> {
            val map = this as EnumEntriesBasedLargeEnumMap<E, V>
            createMutableLargeEnumMap(map.keyWords, map.slots, map.universe)
        }

        else -> GenericMutableEnumMap(toMutableMap())
    }
}

/**
 * Creates a mutable [MutableEnumMap] from [pairs].
 */
public inline fun <reified E : Enum<E>, V> mutableEnumMapOf(vararg pairs: Pair<E, V>): MutableEnumMap<E, V> {
    val universe = enumEntries<E>()
    val map: MutableEnumMap<E, V> = createMutableEnumMap(universe)
    if (pairs.isNotEmpty()) {
        for ((key, value) in pairs) {
            map[key] = value
        }
    }
    return map
}

@PublishedApi
internal fun <E : Enum<E>, V> createMutableEnumMap(universe: EnumEntries<E>): MutableEnumMap<E, V> = when {
    universe.size <= Int.SIZE_BITS -> MutableI32EnumMap(universe)
    universe.size <= Long.SIZE_BITS -> MutableI64EnumMap(universe)
    else -> MutableLargeEnumMap(universe)
}

@PublishedApi
internal fun <E : Enum<E>, V> createMutableI32EnumMap(
    keyBits: Int,
    slots: Array<Any?>,
    universe: EnumEntries<E>
): MutableEnumMap<E, V> {
    val requiredSlotSize = mapHighestOrdinalPlusOne(keyBits)
    val normalizedSlots = slots.copyOf(requiredSlotSize)
    return MutableI32EnumMap(universe, keyBits, normalizedSlots)
}

@PublishedApi
internal fun <E : Enum<E>, V> createMutableI64EnumMap(
    keyBits: Long,
    slots: Array<Any?>,
    universe: EnumEntries<E>
): MutableEnumMap<E, V> {
    val requiredSlotSize = mapHighestOrdinalPlusOne(keyBits)
    val normalizedSlots = slots.copyOf(requiredSlotSize)
    return MutableI64EnumMap(universe, keyBits, normalizedSlots)
}

@PublishedApi
internal fun <E : Enum<E>, V> createMutableLargeEnumMap(
    keyWords: LongArray,
    slots: Array<Any?>,
    universe: EnumEntries<E>
): MutableEnumMap<E, V> {
    val lastWordIndex = mapLastNonZeroWordIndex(keyWords)
    if (lastWordIndex < 0) return MutableLargeEnumMap(universe)

    val trimmedWords = if (lastWordIndex == keyWords.lastIndex) keyWords.copyOf() else keyWords.copyOf(lastWordIndex + 1)
    val requiredSlotSize = maxOf(mapHighestOrdinalPlusOne(trimmedWords), slots.size)
    val normalizedSlots = slots.copyOf(requiredSlotSize)
    return MutableLargeEnumMap(universe, trimmedWords, normalizedSlots, mapBitCountOfWords(trimmedWords))
}

internal interface EnumEntriesBasedI32MutableEnumMap<E : Enum<E>, V> :
    MutableEnumMap<E, V>, EnumEntriesBasedI32EnumMap<E, V> {
    override var keyBits: Int
    override var slots: Array<Any?>
}

internal interface EnumEntriesBasedI64MutableEnumMap<E : Enum<E>, V> :
    MutableEnumMap<E, V>, EnumEntriesBasedI64EnumMap<E, V> {
    override var keyBits: Long
    override var slots: Array<Any?>
}

internal interface EnumEntriesBasedLargeMutableEnumMap<E : Enum<E>, V> :
    MutableEnumMap<E, V>, EnumEntriesBasedLargeEnumMap<E, V> {
    override var keyWords: LongArray
    override var slots: Array<Any?>
    var mapSize: Int
}

private class GenericMutableEnumMap<E : Enum<E>, V>(private val delegate: MutableMap<E, V>) :
    MutableEnumMap<E, V>, MutableMap<E, V> by delegate {
    override fun equals(other: Any?): Boolean = delegate == other

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = delegate.toString()
}

private class MutableI32EnumMap<E : Enum<E>, V>(
    override val universe: EnumEntries<E>,
    override var keyBits: Int = 0,
    override var slots: Array<Any?> = arrayOfNulls(universe.size)
) : AbstractMutableMap<E, V>(), EnumEntriesBasedI32MutableEnumMap<E, V> {

    private fun keyOrdinalOrMinusOne(key: E): Int {
        val ordinal = key.ordinal
        return if (ordinal < universe.size) ordinal else -1
    }

    private fun containsOrdinal(ordinal: Int): Boolean = (keyBits and (1 shl ordinal)) != 0

    @Suppress("UNCHECKED_CAST")
    private fun valueAt(ordinal: Int): V = slots[ordinal] as V

    private fun ensureSlotCapacity(ordinal: Int) {
        if (ordinal < slots.size) return
        slots = slots.copyOf(universe.size)
    }

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

    override fun put(key: E, value: V): V? {
        val ordinal = keyOrdinalOrMinusOne(key)
        if (ordinal < 0) return null

        val mask = 1 shl ordinal
        val existed = (keyBits and mask) != 0
        ensureSlotCapacity(ordinal)
        val oldValue = if (existed) valueAt(ordinal) else null
        slots[ordinal] = value
        keyBits = keyBits or mask
        return oldValue
    }

    override fun remove(key: E): V? {
        val ordinal = keyOrdinalOrMinusOne(key)
        if (ordinal < 0) return null

        val mask = 1 shl ordinal
        if ((keyBits and mask) == 0) return null

        val oldValue = valueAt(ordinal)
        keyBits = keyBits and mask.inv()
        slots[ordinal] = null
        return oldValue
    }

    override fun putAll(from: Map<out E, V>) {
        if (from.isEmpty()) return

        if (from is EnumEntriesBasedI32EnumMap<*, *> && sameMapUniverse(universe, from.universe)) {
            @Suppress("UNCHECKED_CAST")
            val source = from as EnumEntriesBasedI32EnumMap<E, V>
            val sourceBits = source.keyBits
            if (sourceBits == 0) return

            keyBits = keyBits or sourceBits
            var remaining = sourceBits
            while (remaining != 0) {
                val ordinal = remaining.countTrailingZeroBits()
                ensureSlotCapacity(ordinal)
                slots[ordinal] = source.slots[ordinal]
                remaining = remaining and (remaining - 1)
            }
            return
        }

        for ((key, value) in from) {
            put(key, value)
        }
    }

    override fun clear() {
        var remaining = keyBits
        while (remaining != 0) {
            val ordinal = remaining.countTrailingZeroBits()
            slots[ordinal] = null
            remaining = remaining and (remaining - 1)
        }
        keyBits = 0
    }

    private var cachedEntries: MutableSet<MutableMap.MutableEntry<E, V>>? = null

    override val entries: MutableSet<MutableMap.MutableEntry<E, V>>
        get() {
            val cached = cachedEntries
            if (cached != null) return cached
            val created = createEntries()
            cachedEntries = created
            return created
        }

    private fun createEntries(): MutableSet<MutableMap.MutableEntry<E, V>> = object :
        AbstractMutableSet<MutableMap.MutableEntry<E, V>>() {

        override val size: Int
            get() = this@MutableI32EnumMap.size

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<E, V>> = EntryIterator()

        override fun contains(element: MutableMap.MutableEntry<E, V>): Boolean {
            val ordinal = keyOrdinalOrMinusOne(element.key)
            if (ordinal < 0 || !containsOrdinal(ordinal)) return false
            return slots[ordinal] == element.value
        }

        override fun add(element: MutableMap.MutableEntry<E, V>): Boolean {
            val ordinal = keyOrdinalOrMinusOne(element.key)
            if (ordinal < 0) return false

            val mask = 1 shl ordinal
            val existed = (keyBits and mask) != 0
            ensureSlotCapacity(ordinal)
            val oldValue = if (existed) slots[ordinal] else null

            slots[ordinal] = element.value
            keyBits = keyBits or mask
            return !existed || oldValue != element.value
        }

        override fun remove(element: MutableMap.MutableEntry<E, V>): Boolean {
            if (!contains(element)) return false
            this@MutableI32EnumMap.remove(element.key)
            return true
        }

        override fun clear() {
            this@MutableI32EnumMap.clear()
        }
    }

    private inner class EntryIterator : MutableIterator<MutableMap.MutableEntry<E, V>> {
        private var remaining = keyBits
        private var lastOrdinal = -1

        override fun hasNext(): Boolean = remaining != 0

        override fun next(): MutableMap.MutableEntry<E, V> {
            val current = remaining
            if (current == 0) throw NoSuchElementException()
            val ordinal = current.countTrailingZeroBits()
            lastOrdinal = ordinal
            remaining = current and (current - 1)
            return MutableEntryImpl(ordinal)
        }

        override fun remove() {
            check(lastOrdinal >= 0) { "next() must be called before remove()" }
            val ordinal = lastOrdinal
            keyBits = keyBits and (1 shl ordinal).inv()
            slots[ordinal] = null
            lastOrdinal = -1
        }
    }

    private inner class MutableEntryImpl(private val ordinal: Int) : MutableMap.MutableEntry<E, V> {
        override val key: E
            get() = universe[ordinal]

        override val value: V
            get() = valueAt(ordinal)

        override fun setValue(newValue: V): V {
            check(containsOrdinal(ordinal)) { "Entry is no longer valid" }
            val old = valueAt(ordinal)
            slots[ordinal] = newValue
            return old
        }

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
            if (!sameMapUniverse(universe, other.universe)) {
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

private class MutableI64EnumMap<E : Enum<E>, V>(
    override val universe: EnumEntries<E>,
    override var keyBits: Long = 0L,
    override var slots: Array<Any?> = arrayOfNulls(universe.size)
) : AbstractMutableMap<E, V>(), EnumEntriesBasedI64MutableEnumMap<E, V> {

    private fun keyOrdinalOrMinusOne(key: E): Int {
        val ordinal = key.ordinal
        return if (ordinal < universe.size) ordinal else -1
    }

    private fun containsOrdinal(ordinal: Int): Boolean = (keyBits and (1L shl ordinal)) != 0L

    @Suppress("UNCHECKED_CAST")
    private fun valueAt(ordinal: Int): V = slots[ordinal] as V

    private fun ensureSlotCapacity(ordinal: Int) {
        if (ordinal < slots.size) return
        slots = slots.copyOf(universe.size)
    }

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

    override fun put(key: E, value: V): V? {
        val ordinal = keyOrdinalOrMinusOne(key)
        if (ordinal < 0) return null

        val mask = 1L shl ordinal
        val existed = (keyBits and mask) != 0L
        ensureSlotCapacity(ordinal)
        val oldValue = if (existed) valueAt(ordinal) else null
        slots[ordinal] = value
        keyBits = keyBits or mask
        return oldValue
    }

    override fun remove(key: E): V? {
        val ordinal = keyOrdinalOrMinusOne(key)
        if (ordinal < 0) return null

        val mask = 1L shl ordinal
        if ((keyBits and mask) == 0L) return null

        val oldValue = valueAt(ordinal)
        keyBits = keyBits and mask.inv()
        slots[ordinal] = null
        return oldValue
    }

    override fun putAll(from: Map<out E, V>) {
        if (from.isEmpty()) return

        if (from is EnumEntriesBasedI64EnumMap<*, *> && sameMapUniverse(universe, from.universe)) {
            @Suppress("UNCHECKED_CAST")
            val source = from as EnumEntriesBasedI64EnumMap<E, V>
            val sourceBits = source.keyBits
            if (sourceBits == 0L) return

            keyBits = keyBits or sourceBits
            var remaining = sourceBits
            while (remaining != 0L) {
                val ordinal = remaining.countTrailingZeroBits()
                ensureSlotCapacity(ordinal)
                slots[ordinal] = source.slots[ordinal]
                remaining = remaining and (remaining - 1)
            }
            return
        }

        for ((key, value) in from) {
            put(key, value)
        }
    }

    override fun clear() {
        var remaining = keyBits
        while (remaining != 0L) {
            val ordinal = remaining.countTrailingZeroBits()
            slots[ordinal] = null
            remaining = remaining and (remaining - 1)
        }
        keyBits = 0L
    }

    private var cachedEntries: MutableSet<MutableMap.MutableEntry<E, V>>? = null

    override val entries: MutableSet<MutableMap.MutableEntry<E, V>>
        get() {
            val cached = cachedEntries
            if (cached != null) return cached
            val created = createEntries()
            cachedEntries = created
            return created
        }

    private fun createEntries(): MutableSet<MutableMap.MutableEntry<E, V>> = object :
        AbstractMutableSet<MutableMap.MutableEntry<E, V>>() {

        override val size: Int
            get() = this@MutableI64EnumMap.size

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<E, V>> = EntryIterator()

        override fun contains(element: MutableMap.MutableEntry<E, V>): Boolean {
            val ordinal = keyOrdinalOrMinusOne(element.key)
            if (ordinal < 0 || !containsOrdinal(ordinal)) return false
            return slots[ordinal] == element.value
        }

        override fun add(element: MutableMap.MutableEntry<E, V>): Boolean {
            val ordinal = keyOrdinalOrMinusOne(element.key)
            if (ordinal < 0) return false

            val mask = 1L shl ordinal
            val existed = (keyBits and mask) != 0L
            ensureSlotCapacity(ordinal)
            val oldValue = if (existed) slots[ordinal] else null

            slots[ordinal] = element.value
            keyBits = keyBits or mask
            return !existed || oldValue != element.value
        }

        override fun remove(element: MutableMap.MutableEntry<E, V>): Boolean {
            if (!contains(element)) return false
            this@MutableI64EnumMap.remove(element.key)
            return true
        }

        override fun clear() {
            this@MutableI64EnumMap.clear()
        }
    }

    private inner class EntryIterator : MutableIterator<MutableMap.MutableEntry<E, V>> {
        private var remaining = keyBits
        private var lastOrdinal = -1

        override fun hasNext(): Boolean = remaining != 0L

        override fun next(): MutableMap.MutableEntry<E, V> {
            val current = remaining
            if (current == 0L) throw NoSuchElementException()
            val ordinal = current.countTrailingZeroBits()
            lastOrdinal = ordinal
            remaining = current and (current - 1)
            return MutableEntryImpl(ordinal)
        }

        override fun remove() {
            check(lastOrdinal >= 0) { "next() must be called before remove()" }
            val ordinal = lastOrdinal
            keyBits = keyBits and (1L shl ordinal).inv()
            slots[ordinal] = null
            lastOrdinal = -1
        }
    }

    private inner class MutableEntryImpl(private val ordinal: Int) : MutableMap.MutableEntry<E, V> {
        override val key: E
            get() = universe[ordinal]

        override val value: V
            get() = valueAt(ordinal)

        override fun setValue(newValue: V): V {
            check(containsOrdinal(ordinal)) { "Entry is no longer valid" }
            val old = valueAt(ordinal)
            slots[ordinal] = newValue
            return old
        }

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
            if (!sameMapUniverse(universe, other.universe)) {
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

private class MutableLargeEnumMap<E : Enum<E>, V>(
    override val universe: EnumEntries<E>,
    override var keyWords: LongArray = LongArray(0),
    override var slots: Array<Any?> = emptyArray(),
    override var mapSize: Int = 0
) : AbstractMutableMap<E, V>(), EnumEntriesBasedLargeMutableEnumMap<E, V> {

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

    private fun ensureCapacityForOrdinal(ordinal: Int) {
        val requiredWordSize = (ordinal ushr 6) + 1
        if (requiredWordSize > keyWords.size) {
            keyWords = keyWords.copyOf(requiredWordSize)
        }

        if (ordinal < slots.size) return

        val requiredSize = ordinal + 1
        var newSize = if (slots.isEmpty()) minOf(8, universe.size) else slots.size
        while (newSize < requiredSize) {
            val grown = newSize + (newSize ushr 1)
            newSize = if (grown <= newSize) requiredSize else grown
        }

        if (newSize > universe.size) {
            newSize = universe.size
        }
        slots = slots.copyOf(newSize)
    }

    private fun removeByOrdinal(ordinal: Int) {
        val wordIndex = ordinal ushr 6
        if (wordIndex >= keyWords.size) return
        val mask = 1L shl (ordinal and 63)
        if ((keyWords[wordIndex] and mask) == 0L) return

        keyWords[wordIndex] = keyWords[wordIndex] and mask.inv()
        if (ordinal < slots.size) {
            slots[ordinal] = null
        }
        mapSize--

        val lastNonZeroWordIndex = mapLastNonZeroWordIndex(keyWords)
        if (lastNonZeroWordIndex < 0) {
            keyWords = LongArray(0)
            slots = emptyArray()
            mapSize = 0
            return
        }

        if (lastNonZeroWordIndex < keyWords.lastIndex) {
            keyWords = keyWords.copyOf(lastNonZeroWordIndex + 1)
        }

        val requiredSlotSize = mapHighestOrdinalPlusOne(keyWords)
        if (slots.size > requiredSlotSize) {
            slots = slots.copyOf(requiredSlotSize)
        }
    }

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

    override fun put(key: E, value: V): V? {
        val ordinal = keyOrdinalOrMinusOne(key)
        if (ordinal < 0) return null

        ensureCapacityForOrdinal(ordinal)
        val wordIndex = ordinal ushr 6
        val mask = 1L shl (ordinal and 63)
        val existed = (keyWords[wordIndex] and mask) != 0L

        val oldValue = if (existed) valueAt(ordinal) else null
        slots[ordinal] = value
        if (!existed) {
            keyWords[wordIndex] = keyWords[wordIndex] or mask
            mapSize++
        }
        return oldValue
    }

    override fun remove(key: E): V? {
        val ordinal = keyOrdinalOrMinusOne(key)
        if (ordinal < 0 || !containsOrdinal(ordinal)) return null

        val oldValue = if (ordinal < slots.size) valueAt(ordinal) else null
        removeByOrdinal(ordinal)
        return oldValue
    }

    override fun putAll(from: Map<out E, V>) {
        if (from.isEmpty()) return

        if (from is EnumEntriesBasedLargeEnumMap<*, *> && sameMapUniverse(universe, from.universe)) {
            @Suppress("UNCHECKED_CAST")
            val source = from as EnumEntriesBasedLargeEnumMap<E, V>
            val sourceWords = source.keyWords
            if (sourceWords.isEmpty()) return

            if (sourceWords.size > keyWords.size) {
                keyWords = keyWords.copyOf(sourceWords.size)
            }

            val requiredSlots = maxOf(slots.size, source.slots.size)
            if (requiredSlots > slots.size) {
                slots = slots.copyOf(requiredSlots)
            }

            var addedCount = 0
            for (wordIndex in sourceWords.indices) {
                val sourceWord = sourceWords[wordIndex]
                if (sourceWord == 0L) continue

                val oldWord = keyWords[wordIndex]
                val mergedWord = oldWord or sourceWord
                if (mergedWord != oldWord) {
                    addedCount += (mergedWord xor oldWord).countOneBits()
                    keyWords[wordIndex] = mergedWord
                }

                var word = sourceWord
                while (word != 0L) {
                    val bit = word.countTrailingZeroBits()
                    val ordinal = (wordIndex shl 6) + bit
                    if (ordinal < source.slots.size) {
                        slots[ordinal] = source.slots[ordinal]
                    }
                    word = word and (word - 1)
                }
            }

            mapSize += addedCount
            return
        }

        for ((key, value) in from) {
            put(key, value)
        }
    }

    override fun clear() {
        if (mapSize == 0) return
        for (wordIndex in keyWords.indices) {
            var word = keyWords[wordIndex]
            while (word != 0L) {
                val bit = word.countTrailingZeroBits()
                val ordinal = (wordIndex shl 6) + bit
                if (ordinal < slots.size) {
                    slots[ordinal] = null
                }
                word = word and (word - 1)
            }
        }
        keyWords = LongArray(0)
        slots = emptyArray()
        mapSize = 0
    }

    private var cachedEntries: MutableSet<MutableMap.MutableEntry<E, V>>? = null

    override val entries: MutableSet<MutableMap.MutableEntry<E, V>>
        get() {
            val cached = cachedEntries
            if (cached != null) return cached
            val created = createEntries()
            cachedEntries = created
            return created
        }

    private fun createEntries(): MutableSet<MutableMap.MutableEntry<E, V>> = object :
        AbstractMutableSet<MutableMap.MutableEntry<E, V>>() {

        override val size: Int
            get() = mapSize

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<E, V>> = EntryIterator()

        override fun contains(element: MutableMap.MutableEntry<E, V>): Boolean {
            val ordinal = keyOrdinalOrMinusOne(element.key)
            if (ordinal < 0 || !containsOrdinal(ordinal) || ordinal >= slots.size) return false
            return slots[ordinal] == element.value
        }

        override fun add(element: MutableMap.MutableEntry<E, V>): Boolean {
            val ordinal = keyOrdinalOrMinusOne(element.key)
            if (ordinal < 0) return false

            ensureCapacityForOrdinal(ordinal)
            val wordIndex = ordinal ushr 6
            val mask = 1L shl (ordinal and 63)
            val existed = (keyWords[wordIndex] and mask) != 0L
            val oldValue = if (existed) slots[ordinal] else null

            slots[ordinal] = element.value
            if (!existed) {
                keyWords[wordIndex] = keyWords[wordIndex] or mask
                mapSize++
            }

            return !existed || oldValue != element.value
        }

        override fun remove(element: MutableMap.MutableEntry<E, V>): Boolean {
            if (!contains(element)) return false
            this@MutableLargeEnumMap.remove(element.key)
            return true
        }

        override fun clear() {
            this@MutableLargeEnumMap.clear()
        }
    }

    private inner class EntryIterator : MutableIterator<MutableMap.MutableEntry<E, V>> {
        private var currentWordIndex = 0
        private var currentWord = if (keyWords.isNotEmpty()) keyWords[0] else 0L
        private var lastOrdinal = -1

        override fun hasNext(): Boolean {
            while (currentWord == 0L && currentWordIndex < keyWords.lastIndex) {
                currentWordIndex++
                currentWord = keyWords[currentWordIndex]
            }
            return currentWord != 0L
        }

        override fun next(): MutableMap.MutableEntry<E, V> {
            if (!hasNext()) throw NoSuchElementException()
            val bit = currentWord.countTrailingZeroBits()
            val ordinal = (currentWordIndex shl 6) + bit
            lastOrdinal = ordinal
            currentWord = currentWord and (currentWord - 1)
            return MutableEntryImpl(ordinal)
        }

        override fun remove() {
            check(lastOrdinal >= 0) { "next() must be called before remove()" }
            val ordinal = lastOrdinal
            removeByOrdinal(ordinal)
            if (currentWordIndex >= keyWords.size) {
                currentWordIndex = keyWords.size
                currentWord = 0L
            } else {
                currentWord = if (currentWordIndex < keyWords.size) keyWords[currentWordIndex] and currentWord else 0L
            }
            lastOrdinal = -1
        }
    }

    private inner class MutableEntryImpl(private val ordinal: Int) : MutableMap.MutableEntry<E, V> {
        override val key: E
            get() = universe[ordinal]

        override val value: V
            get() = valueAt(ordinal)

        override fun setValue(newValue: V): V {
            check(containsOrdinal(ordinal)) { "Entry is no longer valid" }
            val old = valueAt(ordinal)
            slots[ordinal] = newValue
            return old
        }

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
            if (!sameMapUniverse(universe, other.universe)) {
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
