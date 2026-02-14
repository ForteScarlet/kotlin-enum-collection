package love.forte.tools.enumcollection.examples.api

import love.forte.tools.enumcollection.api.enumMapOf
import love.forte.tools.enumcollection.api.enumSetOf
import love.forte.tools.enumcollection.api.toEnumMap
import love.forte.tools.enumcollection.api.toEnumSet
import love.forte.tools.enumcollection.api.toMutableEnumMap
import love.forte.tools.enumcollection.api.toMutableEnumSet

private enum class Role {
    USER,
    ADMIN,
    GUEST,
}

public fun main() {
    val base = enumSetOf(Role.USER, Role.ADMIN)
    val other = enumSetOf(Role.ADMIN, Role.GUEST)

    println("=== EnumSet (API) ===")
    println("base = $base")
    println("containsAny([GUEST, USER]) = ${base.containsAny(listOf(Role.GUEST, Role.USER))}")
    println("intersect = ${base.intersect(other)}")
    println("union = ${base.union(other)}")
    println("difference = ${base.difference(other)}")

    val mutable = base.toMutableEnumSet()
    val snapshot = mutable.toEnumSet()
    mutable.add(Role.GUEST)
    println("snapshot (before add) = $snapshot")
    println("mutable (after add GUEST) = $mutable")
    println("snapshot (still) = $snapshot")

    println()
    println("=== EnumMap (API) ===")
    val baseMap = enumMapOf(Role.USER to 1, Role.ADMIN to 2)
    println("baseMap = $baseMap")

    val mutableMap = baseMap.toMutableEnumMap()
    val mapSnapshot = mutableMap.toEnumMap()
    mutableMap[Role.GUEST] = 3
    println("mapSnapshot (before put) = $mapSnapshot")
    println("mutableMap (after put GUEST) = $mutableMap")
    println("mapSnapshot (still) = $mapSnapshot")
}
