package love.forte.tools.enumcollection.examples.ksp

public fun main() {
    println("=== EnumSet (KSP) ===")
    val base = RoleEnumSet(Role.USER, Role.ADMIN)
    val other = RoleEnumSet(Role.ADMIN, Role.GUEST)
    println("base = $base")
    println("containsAny([GUEST, USER]) = ${base.containsAny(listOf(Role.GUEST, Role.USER))}")
    println("intersect = ${base.intersect(other)}")
    println("union = ${base.union(other)}")
    println("difference = ${base.difference(other)}")

    val mutable = base.mutable()
    val snapshot = mutable.immutable()
    mutable.add(Role.GUEST)
    println("snapshot (before add) = $snapshot")
    println("mutable = $mutable")
    println("snapshot (still) = $snapshot")

    println()
    println("=== EnumMap (KSP) ===")
    val map = RoleEnumMap(Role.USER to 1, Role.ADMIN to 2)
    println("map = $map")

    val mutableMap = map.mutable()
    val mapSnapshot = mutableMap.immutable()
    mutableMap[Role.GUEST] = 3
    println("mapSnapshot (before put) = $mapSnapshot")
    println("mutableMap = $mutableMap")
    println("mapSnapshot (still) = $mapSnapshot")

    println()
    println("=== Wordset enum (70 entries) ===")
    val bigSet = BigKeyEnumSet(BigKey.E0, BigKey.E69)
    println("bigSet size = ${bigSet.size}")
    val bigMap = BigKeyEnumMap(BigKey.E0 to "zero", BigKey.E69 to "last")
    println("bigMap size = ${bigMap.size}")
}
