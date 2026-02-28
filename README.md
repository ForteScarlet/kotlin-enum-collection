# Kotlin Enum Collection

An enum collection library for Kotlin, providing utilities for working with enums in a more convenient and efficient
way.

- [API module](kotlin-enum-collection-api): contains the core API for enum collection.
- [KSP module](kotlin-enum-collection-ksp): it provides KSP (Kotlin Symbol Processing) integration for generating enum
  collection classes at compile time. Based on [CodeGentle](https://github.com/ForteScarlet/CodeGentle).

## Usage

Add `@EnumSet`/`@EnumMap` on your enum class:

```Kotlin
@EnumSet
@EnumMap
internal enum class Role {
    USER,
    ADMIN,
    GUEST,
}
```

Generated:

**RoleEnumSet**

```Kotlin
internal interface RoleEnumSet /* : EnumSet<Role> */ { // inherit `EnumSet`/`EnumMap` when (inheritance mode == AUTO && inheritance available) || nheritance mode == ALWAYS
    fun containsAny(elements: Collection<Role>): Boolean

    fun intersect(other: Set<Role>): Set<Role>

    fun union(other: Set<Role>): Set<Role>

    fun difference(other: Set<Role>): Set<Role>
}

internal interface MutableRoleEnumSet : RoleEnumSet, MutableSet<Role> /*, MutableEnumSet<Role>*/ {
    fun copy(): MutableRoleEnumSet
}


internal fun RoleEnumSet.mutable(): MutableRoleEnumSet

internal fun MutableRoleEnumSet.immutable(): RoleEnumSet

internal fun RoleEnumSet(full: Boolean = false): RoleEnumSet

internal fun RoleEnumSet(vararg entries: Role): RoleEnumSet

internal fun RoleEnumSet(entries: Iterable<Role>): RoleEnumSet

internal fun MutableRoleEnumSet(full: Boolean = false): MutableRoleEnumSet

internal fun MutableRoleEnumSet(vararg entries: Role): MutableRoleEnumSet

internal fun MutableRoleEnumSet(entries: Iterable<Role>): MutableRoleEnumSet
```

**RoleEnumMap**

```Kotlin
internal interface RoleEnumMap<V> : Map<Role, V> /*, EnumMap<Role, V> */

internal interface MutableRoleEnumMap<V> : RoleEnumMap<V>, MutableMap<Role, V> /*, MutableEnumMap<Role, V> */ {
    override fun copy(): MutableRoleEnumMap<V>
}

internal fun <V> RoleEnumMap<V>.mutable(): MutableRoleEnumMap<V>

internal fun <V> MutableRoleEnumMap<V>.immutable(): RoleEnumMap<V>

internal fun <V> RoleEnumMap(): RoleEnumMap<V>

internal fun <V> RoleEnumMap(vararg pairs: Pair<Role, V>): RoleEnumMap<V>

internal fun <V> RoleEnumMap(from: Map<Role, V>): RoleEnumMap<V>

internal fun <V> MutableRoleEnumMap(): MutableRoleEnumMap<V>

internal fun <V> MutableRoleEnumMap(vararg pairs: Pair<Role, V>): MutableRoleEnumMap<V>

internal fun <V> MutableRoleEnumMap(from: Map<Role, V>): MutableRoleEnumMap<V>
```