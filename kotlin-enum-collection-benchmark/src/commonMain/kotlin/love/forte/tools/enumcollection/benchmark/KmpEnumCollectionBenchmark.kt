package love.forte.tools.enumcollection.benchmark

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import love.forte.tools.enumcollection.api.EnumMap
import love.forte.tools.enumcollection.api.EnumSet
import love.forte.tools.enumcollection.api.MutableEnumMap
import love.forte.tools.enumcollection.api.MutableEnumSet
import love.forte.tools.enumcollection.api.enumMapOf
import love.forte.tools.enumcollection.api.enumSetOf
import love.forte.tools.enumcollection.api.mutableEnumMapOf
import love.forte.tools.enumcollection.api.mutableEnumSetOf
import love.forte.tools.enumcollection.api.toEnumMap
import love.forte.tools.enumcollection.api.toEnumSet
import love.forte.tools.enumcollection.api.toMutableEnumMap
import love.forte.tools.enumcollection.api.toMutableEnumSet

@State(Scope.Benchmark)
public open class KmpEnumCollectionBenchmark {
    private lateinit var smallLeft: EnumSet<SmallEnum>
    private lateinit var smallRight: EnumSet<SmallEnum>
    private lateinit var largeLeft: EnumSet<LargeEnum>
    private lateinit var largeRight: EnumSet<LargeEnum>

    private lateinit var mutableSmallSet: MutableEnumSet<SmallEnum>
    private lateinit var immutableSmallSet: EnumSet<SmallEnum>

    private lateinit var mutableSmallMap: MutableEnumMap<SmallEnum, Int>
    private lateinit var immutableSmallMap: EnumMap<SmallEnum, Int>

    @Setup
    public fun setup() {
        smallLeft = enumSetOf(
            SmallEnum.A0,
            SmallEnum.A2,
            SmallEnum.A4,
            SmallEnum.A6,
            SmallEnum.A8,
            SmallEnum.A10,
            SmallEnum.A12,
            SmallEnum.A14,
            SmallEnum.A16,
            SmallEnum.A18,
            SmallEnum.A20,
            SmallEnum.A22,
            SmallEnum.A24,
            SmallEnum.A26,
            SmallEnum.A28,
            SmallEnum.A30
        )
        smallRight = enumSetOf(
            SmallEnum.A1,
            SmallEnum.A2,
            SmallEnum.A3,
            SmallEnum.A4,
            SmallEnum.A5,
            SmallEnum.A6,
            SmallEnum.A7,
            SmallEnum.A8,
            SmallEnum.A9,
            SmallEnum.A10,
            SmallEnum.A11,
            SmallEnum.A12
        )

        val left = mutableEnumSetOf<LargeEnum>()
        val right = mutableEnumSetOf<LargeEnum>()
        for (entry in LargeEnum.entries) {
            if ((entry.ordinal and 1) == 0) {
                left.add(entry)
            }
            if ((entry.ordinal and 2) == 0) {
                right.add(entry)
            }
        }
        largeLeft = left
        largeRight = right

        mutableSmallSet = mutableEnumSetOf(
            SmallEnum.A0,
            SmallEnum.A3,
            SmallEnum.A6,
            SmallEnum.A9,
            SmallEnum.A12,
            SmallEnum.A15,
            SmallEnum.A18,
            SmallEnum.A21
        )
        immutableSmallSet = enumSetOf(
            SmallEnum.A1,
            SmallEnum.A4,
            SmallEnum.A7,
            SmallEnum.A10,
            SmallEnum.A13,
            SmallEnum.A16,
            SmallEnum.A19,
            SmallEnum.A22
        )

        mutableSmallMap = mutableEnumMapOf(
            SmallEnum.A0 to 0,
            SmallEnum.A1 to 1,
            SmallEnum.A2 to 2,
            SmallEnum.A3 to 3,
            SmallEnum.A4 to 4,
            SmallEnum.A5 to 5,
            SmallEnum.A6 to 6,
            SmallEnum.A7 to 7
        )
        immutableSmallMap = enumMapOf(
            SmallEnum.A8 to 8,
            SmallEnum.A9 to 9,
            SmallEnum.A10 to 10,
            SmallEnum.A11 to 11,
            SmallEnum.A12 to 12,
            SmallEnum.A13 to 13,
            SmallEnum.A14 to 14,
            SmallEnum.A15 to 15
        )
    }

    @Benchmark
    public fun enumSetUnionSmall(): Int = smallLeft.union(smallRight).size

    @Benchmark
    public fun enumSetIntersectSmall(): Int = smallLeft.intersect(smallRight).size

    @Benchmark
    public fun enumSetDifferenceSmall(): Int = smallLeft.difference(smallRight).size

    @Benchmark
    public fun enumSetContainsAnyLarge(): Boolean = largeLeft.containsAny(largeRight)

    @Benchmark
    public fun enumSetToEnumSetSnapshot(): Int {
        val snapshot = mutableSmallSet.toEnumSet()
        return snapshot.size + if (snapshot.contains(SmallEnum.A6)) 1 else 0
    }

    @Benchmark
    public fun enumSetToMutableEnumSetCopy(): Int {
        val copy = immutableSmallSet.toMutableEnumSet()
        copy.add(SmallEnum.A31)
        return copy.size
    }

    @Benchmark
    public fun enumSetMutableMidAddRemove(): Int {
        val set = mutableEnumSetOf<MidEnum>()
        for (entry in MidEnum.entries) {
            if ((entry.ordinal and 1) == 0) {
                set.add(entry)
            }
        }
        for (entry in MidEnum.entries) {
            if ((entry.ordinal and 3) == 0) {
                set.remove(entry)
            }
        }
        return set.size
    }

    @Benchmark
    public fun enumMapSmallPutGetRemove(): Int {
        val map = mutableEnumMapOf<SmallEnum, Int>()
        for (entry in SmallEnum.entries) {
            map[entry] = entry.ordinal
        }

        var sum = 0
        for (entry in SmallEnum.entries) {
            sum += map[entry] ?: 0
        }

        for (entry in SmallEnum.entries) {
            if ((entry.ordinal and 3) == 0) {
                map.remove(entry)
            }
        }

        return sum + map.size
    }

    @Benchmark
    public fun enumMapLargePutAll(): Int {
        val left = mutableEnumMapOf<LargeEnum, Int>()
        val right = mutableEnumMapOf<LargeEnum, Int>()
        for (entry in LargeEnum.entries) {
            if ((entry.ordinal and 1) == 0) {
                left[entry] = entry.ordinal
            }
            if ((entry.ordinal and 2) == 0) {
                right[entry] = entry.ordinal * 2
            }
        }
        left.putAll(right)
        return left.size + (left[LargeEnum.C00] ?: 0)
    }

    @Benchmark
    public fun enumMapImmutableCreate(): Int {
        val map = enumMapOf(
            SmallEnum.A0 to 0,
            SmallEnum.A1 to 1,
            SmallEnum.A2 to 2,
            SmallEnum.A3 to 3,
            SmallEnum.A4 to 4,
            SmallEnum.A5 to 5,
            SmallEnum.A6 to 6,
            SmallEnum.A7 to 7
        )
        return map.size + (map[SmallEnum.A6] ?: 0)
    }

    @Benchmark
    public fun enumMapToEnumMapSnapshot(): Int {
        val snapshot = mutableSmallMap.toEnumMap()
        return snapshot.size + (snapshot[SmallEnum.A4] ?: 0)
    }

    @Benchmark
    public fun enumMapToMutableEnumMapCopy(): Int {
        val copy = immutableSmallMap.toMutableEnumMap()
        copy[SmallEnum.A0] = 100
        return copy.size + (copy[SmallEnum.A0] ?: 0)
    }
}
