package love.forte.tools.enumcollection.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private enum class SmallMapEnum {
    A0, A1, A2, A3, A4, A5, A6, A7,
    A8, A9, A10, A11, A12, A13, A14, A15,
    A16, A17, A18, A19, A20, A21, A22, A23,
    A24, A25, A26, A27, A28, A29, A30, A31
}

private enum class MidMapEnum {
    B0, B1, B2, B3, B4, B5, B6, B7,
    B8, B9, B10, B11, B12, B13, B14, B15,
    B16, B17, B18, B19, B20, B21, B22, B23,
    B24, B25, B26, B27, B28, B29, B30, B31,
    B32, B33, B34, B35, B36, B37, B38, B39,
    B40, B41, B42, B43, B44, B45, B46, B47,
    B48, B49, B50, B51, B52, B53, B54, B55,
    B56, B57, B58, B59, B60, B61, B62, B63
}

private enum class LargeMapEnum {
    C00, C01, C02, C03, C04, C05, C06, C07,
    C08, C09, C10, C11, C12, C13, C14, C15,
    C16, C17, C18, C19, C20, C21, C22, C23,
    C24, C25, C26, C27, C28, C29, C30, C31,
    C32, C33, C34, C35, C36, C37, C38, C39,
    C40, C41, C42, C43, C44, C45, C46, C47,
    C48, C49, C50, C51, C52, C53, C54, C55,
    C56, C57, C58, C59, C60, C61, C62, C63,
    C64, C65, C66, C67, C68, C69, C70, C71,
    C72, C73, C74, C75, C76, C77, C78, C79,
    C80, C81, C82, C83, C84, C85, C86, C87,
    C88, C89, C90, C91, C92, C93, C94, C95
}

class EnumMapTest {
    @Test
    fun emptyEnumMapBasics() {
        val map = emptyEnumMapOf<SmallMapEnum, Int>()
        assertTrue(map.isEmpty())
        assertEquals(0, map.size)
    }

    @Test
    fun i32MutableMapPutGetRemove() {
        val map = mutableEnumMapOf<SmallMapEnum, Int>()
        assertTrue(map.isEmpty())

        assertNull(map.put(SmallMapEnum.A1, 1))
        assertEquals(1, map[SmallMapEnum.A1])
        assertEquals(1, map.size)

        assertEquals(1, map.put(SmallMapEnum.A1, 11))
        assertEquals(11, map[SmallMapEnum.A1])

        assertEquals(11, map.remove(SmallMapEnum.A1))
        assertNull(map.remove(SmallMapEnum.A1))
        assertTrue(map.isEmpty())
    }

    @Test
    fun i64MapPutAllFastPath() {
        val source = mutableEnumMapOf<MidMapEnum, Int>()
        val target = mutableEnumMapOf<MidMapEnum, Int>()

        for (entry in MidMapEnum.entries) {
            if ((entry.ordinal and 1) == 0) {
                source[entry] = entry.ordinal
            }
        }

        target.putAll(source)
        assertEquals(source.size, target.size)
        for ((key, value) in source) {
            assertEquals(value, target[key])
        }
    }

    @Test
    fun largeMapPutAllAndTrimByRemove() {
        val map = mutableEnumMapOf<LargeMapEnum, Int>()
        map[LargeMapEnum.C95] = 95
        map[LargeMapEnum.C00] = 0
        assertEquals(2, map.size)

        assertEquals(95, map.remove(LargeMapEnum.C95))
        assertEquals(1, map.size)
        assertEquals(0, map[LargeMapEnum.C00])
        assertFalse(map.containsKey(LargeMapEnum.C95))
    }

    @Test
    fun immutableFactoryAndEquality() {
        val a = enumMapOf(
            SmallMapEnum.A0 to 0,
            SmallMapEnum.A1 to 1,
            SmallMapEnum.A2 to 2
        )
        val b = mutableEnumMapOf<SmallMapEnum, Int>()
        b[SmallMapEnum.A0] = 0
        b[SmallMapEnum.A1] = 1
        b[SmallMapEnum.A2] = 2

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun entrySetMutationReflectsMap() {
        val map = mutableEnumMapOf<SmallMapEnum, Int>()
        map[SmallMapEnum.A4] = 4

        val entry = map.entries.first()
        assertEquals(4, entry.setValue(40))
        assertEquals(40, map[SmallMapEnum.A4])

        val iterator = map.entries.iterator()
        assertTrue(iterator.hasNext())
        iterator.next()
        iterator.remove()
        assertTrue(map.isEmpty())
    }

    @Test
    fun toEnumMapFromMutableCreatesSnapshot() {
        val mutable = mutableEnumMapOf(
            SmallMapEnum.A0 to 0,
            SmallMapEnum.A1 to 1
        )
        val immutable = mutable.toEnumMap()

        mutable[SmallMapEnum.A0] = 100
        mutable[SmallMapEnum.A2] = 2

        assertEquals(2, immutable.size)
        assertEquals(0, immutable[SmallMapEnum.A0])
        assertFalse(immutable.containsKey(SmallMapEnum.A2))
    }

    @Test
    fun toMutableEnumMapFromImmutableCreatesCopy() {
        val immutable = enumMapOf(
            SmallMapEnum.A3 to 3,
            SmallMapEnum.A4 to 4
        )
        val mutable = immutable.toMutableEnumMap()

        mutable[SmallMapEnum.A3] = 30
        mutable[SmallMapEnum.A5] = 5

        assertEquals(2, immutable.size)
        assertEquals(3, immutable[SmallMapEnum.A3])
        assertFalse(immutable.containsKey(SmallMapEnum.A5))

        assertEquals(3, mutable.size)
        assertEquals(30, mutable[SmallMapEnum.A3])
    }

    @Test
    fun toMutableEnumMapFromMutableCreatesIndependentCopy() {
        val mutable = mutableEnumMapOf(SmallMapEnum.A9 to 9, SmallMapEnum.A10 to 10)
        val copy = mutable.toMutableEnumMap()

        mutable[SmallMapEnum.A11] = 11
        copy.remove(SmallMapEnum.A9)

        assertTrue(mutable.containsKey(SmallMapEnum.A11))
        assertFalse(copy.containsKey(SmallMapEnum.A9))
        assertEquals(3, mutable.size)
        assertEquals(1, copy.size)
    }

    @Test
    fun mutableCopyIsIndependent() {
        val original = mutableEnumMapOf(SmallMapEnum.A1 to 1, SmallMapEnum.A2 to 2)
        val copy = original.copy()

        assertFalse(copy === original)

        copy[SmallMapEnum.A3] = 3
        original.remove(SmallMapEnum.A1)

        assertFalse(original.containsKey(SmallMapEnum.A3))
        assertEquals(1, original.size)
        assertEquals(3, copy.size)
    }

    @Test
    fun mutableLargeCopyIsDeep() {
        val original = mutableEnumMapOf<LargeMapEnum, Int>()
        original[LargeMapEnum.C00] = 0
        original[LargeMapEnum.C95] = 95

        val copy = original.copy()
        copy.remove(LargeMapEnum.C95)
        copy[LargeMapEnum.C01] = 1

        assertTrue(original.containsKey(LargeMapEnum.C95))
        assertFalse(original.containsKey(LargeMapEnum.C01))
        assertEquals(2, original.size)
        assertEquals(2, copy.size)
    }

    @Test
    fun plainMapConversionCreatesIndependentMutableCopy() {
        val plain = linkedMapOf(SmallMapEnum.A6 to 6, SmallMapEnum.A7 to 7)
        val mutable = plain.toMutableEnumMap()
        mutable[SmallMapEnum.A8] = 8

        assertEquals(2, plain.size)
        assertFalse(plain.containsKey(SmallMapEnum.A8))
        assertEquals(3, mutable.size)
    }
}
