package love.forte.tools.enumcollection.examples.api

import love.forte.tools.enumcollection.api.EnumMap
import love.forte.tools.enumcollection.api.EnumSet
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

public fun main(): Unit {
    val set: EnumSet<Role> = enumSetOf(Role.USER, Role.ADMIN)
    check(set.contains(Role.USER))
    check(!set.contains(Role.GUEST))

    val mutable = set.toMutableEnumSet()
    mutable.add(Role.GUEST)

    val snapshot = mutable.toEnumSet()
    check(snapshot.contains(Role.GUEST))

    println("EnumSet example: $set -> mutable=$mutable -> snapshot=$snapshot")

    val map: EnumMap<Role, Int> = enumMapOf(Role.USER to 1, Role.ADMIN to 2)
    check(map[Role.ADMIN] == 2)

    val mutableMap = map.toMutableEnumMap()
    mutableMap[Role.GUEST] = 3

    val immutableMap = mutableMap.toEnumMap()
    check(immutableMap[Role.GUEST] == 3)

    println("EnumMap example: $map -> mutable=$mutableMap -> snapshot=$immutableMap")
}
