package love.forte.tools.enumcollection.api

import kotlin.enums.EnumEntries

internal fun sameUniverse(left: EnumEntries<*>, right: EnumEntries<*>): Boolean {
    if (left === right) return true
    val size = left.size
    if (size != right.size) return false
    if (size == 0) return true
    return left[0] === right[0] && left[size - 1] === right[size - 1]
}
