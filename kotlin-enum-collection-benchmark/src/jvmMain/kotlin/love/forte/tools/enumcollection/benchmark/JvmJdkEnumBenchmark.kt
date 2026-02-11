package love.forte.tools.enumcollection.benchmark

import java.util.EnumMap
import java.util.EnumSet
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

@State(Scope.Benchmark)
public open class JvmJdkEnumBenchmark {
    private lateinit var smallLeft: EnumSet<SmallEnum>
    private lateinit var smallRight: EnumSet<SmallEnum>
    private lateinit var largeLeft: EnumSet<LargeEnum>
    private lateinit var largeRight: EnumSet<LargeEnum>

    private lateinit var mutableSmallSet: EnumSet<SmallEnum>
    private lateinit var immutableSmallSet: EnumSet<SmallEnum>

    private lateinit var mutableSmallMap: EnumMap<SmallEnum, Int>
    private lateinit var immutableSmallMap: EnumMap<SmallEnum, Int>

    @Setup
    public fun setup() {
        smallLeft = EnumSet.of(
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
        smallRight = EnumSet.of(
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

        largeLeft = EnumSet.noneOf(LargeEnum::class.java)
        largeRight = EnumSet.noneOf(LargeEnum::class.java)
        for (entry in LargeEnum.entries) {
            if ((entry.ordinal and 1) == 0) {
                largeLeft.add(entry)
            }
            if ((entry.ordinal and 2) == 0) {
                largeRight.add(entry)
            }
        }

        mutableSmallSet = EnumSet.of(
            SmallEnum.A0,
            SmallEnum.A3,
            SmallEnum.A6,
            SmallEnum.A9,
            SmallEnum.A12,
            SmallEnum.A15,
            SmallEnum.A18,
            SmallEnum.A21
        )
        immutableSmallSet = EnumSet.of(
            SmallEnum.A1,
            SmallEnum.A4,
            SmallEnum.A7,
            SmallEnum.A10,
            SmallEnum.A13,
            SmallEnum.A16,
            SmallEnum.A19,
            SmallEnum.A22
        )

        mutableSmallMap = EnumMap<SmallEnum, Int>(SmallEnum::class.java).apply {
            put(SmallEnum.A0, 0)
            put(SmallEnum.A1, 1)
            put(SmallEnum.A2, 2)
            put(SmallEnum.A3, 3)
            put(SmallEnum.A4, 4)
            put(SmallEnum.A5, 5)
            put(SmallEnum.A6, 6)
            put(SmallEnum.A7, 7)
        }
        immutableSmallMap = EnumMap<SmallEnum, Int>(SmallEnum::class.java).apply {
            put(SmallEnum.A8, 8)
            put(SmallEnum.A9, 9)
            put(SmallEnum.A10, 10)
            put(SmallEnum.A11, 11)
            put(SmallEnum.A12, 12)
            put(SmallEnum.A13, 13)
            put(SmallEnum.A14, 14)
            put(SmallEnum.A15, 15)
        }
    }

    @Benchmark
    public fun jdkEnumSetUnionSmall(): Int {
        val result = EnumSet.copyOf(smallLeft)
        result.addAll(smallRight)
        return result.size
    }

    @Benchmark
    public fun jdkEnumSetIntersectSmall(): Int {
        val result = EnumSet.copyOf(smallLeft)
        result.retainAll(smallRight)
        return result.size
    }

    @Benchmark
    public fun jdkEnumSetDifferenceSmall(): Int {
        val result = EnumSet.copyOf(smallLeft)
        result.removeAll(smallRight)
        return result.size
    }

    @Benchmark
    public fun jdkEnumSetContainsAnyLarge(): Boolean {
        val result = EnumSet.copyOf(largeLeft)
        result.retainAll(largeRight)
        return result.isNotEmpty()
    }

    @Benchmark
    public fun jdkEnumSetSnapshotCopy(): Int {
        val snapshot = EnumSet.copyOf(mutableSmallSet)
        return snapshot.size + if (snapshot.contains(SmallEnum.A6)) 1 else 0
    }

    @Benchmark
    public fun jdkEnumSetMutableCopy(): Int {
        val copy = EnumSet.copyOf(immutableSmallSet)
        copy.add(SmallEnum.A31)
        return copy.size
    }

    @Benchmark
    public fun jdkEnumSetMutableMidAddRemove(): Int {
        val set = EnumSet.noneOf(MidEnum::class.java)
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
    public fun jdkEnumMapSmallPutGetRemove(): Int {
        val map = EnumMap<SmallEnum, Int>(SmallEnum::class.java)
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
    public fun jdkEnumMapLargePutAll(): Int {
        val left = EnumMap<LargeEnum, Int>(LargeEnum::class.java)
        val right = EnumMap<LargeEnum, Int>(LargeEnum::class.java)
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
    public fun jdkEnumMapImmutableLikeCreate(): Int {
        val map = EnumMap<SmallEnum, Int>(SmallEnum::class.java)
        map[SmallEnum.A0] = 0
        map[SmallEnum.A1] = 1
        map[SmallEnum.A2] = 2
        map[SmallEnum.A3] = 3
        map[SmallEnum.A4] = 4
        map[SmallEnum.A5] = 5
        map[SmallEnum.A6] = 6
        map[SmallEnum.A7] = 7
        return map.size + (map[SmallEnum.A6] ?: 0)
    }

    @Benchmark
    public fun jdkEnumMapSnapshotCopy(): Int {
        val snapshot = EnumMap(mutableSmallMap)
        return snapshot.size + (snapshot[SmallEnum.A4] ?: 0)
    }

    @Benchmark
    public fun jdkEnumMapMutableCopy(): Int {
        val copy = EnumMap(immutableSmallMap)
        copy[SmallEnum.A0] = 100
        return copy.size + (copy[SmallEnum.A0] ?: 0)
    }
}
