package love.forte.tools.enumcollection.api

import kotlin.enums.enumEntries


/**
 * An [Enum] [Set].
 * 
 * @author ForteScarlet 
 */
public sealed interface EnumSet<T : Enum<T>> : Set<T> {
}


public inline fun <reified T : Enum<T>> fullEnumSetOf(): EnumSet<T> {
    val entries = enumEntries<T>()

    TODO()
}

@Suppress("UNCHECKED_CAST")
public fun <T : Enum<T>> emptyEnumSetOf(): EnumSet<T> = EmptyEnumSet as EnumSet<T>

private object EmptyEnumSet : EnumSet<Nothing> {
    override fun contains(element: Nothing): Boolean = false
    override fun containsAll(elements: Collection<Nothing>): Boolean = false
    override fun isEmpty(): Boolean = true
    override fun iterator(): Iterator<Nothing> = emptyList<Nothing>().iterator()
    override val size: Int get() = 0
}

internal class EnumSet32<T : Enum<T>> : EnumSet<T> {
    override fun contains(element: T): Boolean {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    override fun iterator(): Iterator<T> {
        TODO("Not yet implemented")
    }

    override val size: Int
        get() = TODO("Not yet implemented")
}
