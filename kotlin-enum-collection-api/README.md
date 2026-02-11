# kotlin-enum-collection-api

`kotlin-enum-collection-api` provides collection types specialized for `enum` keys/elements:

- `EnumSet<E>` / `MutableEnumSet<E>`
- `EnumMap<E, V>` / `MutableEnumMap<E, V>`

These APIs are built on `ordinal`-based bitset/slot storage, which usually reduces allocations and keeps constant-time operations predictable.

## 1. EnumSet

### 1.1 Immutable set

```kotlin
import love.forte.tools.enumcollection.api.EnumSet
import love.forte.tools.enumcollection.api.enumSetOf
import love.forte.tools.enumcollection.api.fullEnumSetOf

enum class Role { USER, ADMIN, GUEST }

val base: EnumSet<Role> = enumSetOf(Role.USER, Role.ADMIN)
val all: EnumSet<Role> = fullEnumSetOf<Role>()

val union = base.union(setOf(Role.GUEST))
val intersect = base.intersect(setOf(Role.ADMIN, Role.GUEST))
val diff = base.difference(setOf(Role.USER))
val hasAny = base.containsAny(listOf(Role.GUEST, Role.ADMIN))
```

Common factory functions:

- `enumSetOf(vararg elements)`: create an immutable `EnumSet`
- `fullEnumSetOf<E>()`: create an `EnumSet` containing all enum constants
- `emptyEnumSetOf<E>()`: create an empty `EnumSet`

### 1.2 Mutable set

```kotlin
import love.forte.tools.enumcollection.api.mutableEnumSetOf

val mutable = mutableEnumSetOf(Role.USER)
mutable.add(Role.ADMIN)
mutable.remove(Role.USER)

val copy = mutable.copy() // independent copy
copy.add(Role.GUEST)
```

### 1.3 Conversion

```kotlin
import love.forte.tools.enumcollection.api.toEnumSet
import love.forte.tools.enumcollection.api.toMutableEnumSet

val plain = linkedSetOf(Role.USER, Role.ADMIN)
val immutableSet = plain.toEnumSet()
val mutableSet = immutableSet.toMutableEnumSet()
```

## 2. EnumMap

### 2.1 Immutable map

```kotlin
import love.forte.tools.enumcollection.api.EnumMap
import love.forte.tools.enumcollection.api.enumMapOf

enum class State { INIT, RUNNING, STOPPED }

val stateCode: EnumMap<State, Int> = enumMapOf(
    State.INIT to 0,
    State.RUNNING to 1
)

val runningCode = stateCode[State.RUNNING]
val hasInit = stateCode.containsKey(State.INIT)
```

Common factory functions:

- `enumMapOf(vararg pairs)`: create an immutable `EnumMap`
- `emptyEnumMapOf<E, V>()`: create an empty `EnumMap`

### 2.2 Mutable map

```kotlin
import love.forte.tools.enumcollection.api.mutableEnumMapOf

val mutableMap = mutableEnumMapOf<State, Int>()
mutableMap[State.INIT] = 0
mutableMap[State.RUNNING] = 1

val copy = mutableMap.copy() // independent copy
copy[State.STOPPED] = 2
```

### 2.3 Conversion

```kotlin
import love.forte.tools.enumcollection.api.toEnumMap
import love.forte.tools.enumcollection.api.toMutableEnumMap

val plain = linkedMapOf(State.INIT to 0, State.RUNNING to 1)
val immutableMap = plain.toEnumMap()
val mutableMap = immutableMap.toMutableEnumMap()
```

## 3. Copy semantics

- Immutable `EnumSet` / `EnumMap` do **not** provide `copy()`.
- Mutable `MutableEnumSet` / `MutableEnumMap` provide `copy()`, returning an independent clone.
- To get a mutable clone from an immutable object, use:
  - `toMutableEnumSet()`
  - `toMutableEnumMap()`

## 4. When to use

- Your key/element type is `enum`, and you frequently run membership checks, set operations, or enum-keyed lookups.
- You want to avoid extra allocation/hash overhead from general-purpose `Set`/`Map` implementations.
