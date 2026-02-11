package love.forte.tools.enumcollection.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private enum class SmallSetEnum {
    A0, A1, A2, A3, A4, A5, A6, A7,
    A8, A9, A10, A11, A12, A13, A14, A15,
    A16, A17, A18, A19, A20, A21, A22, A23,
    A24, A25, A26, A27, A28, A29, A30, A31
}

private enum class MidSetEnum {
    B0, B1, B2, B3, B4, B5, B6, B7,
    B8, B9, B10, B11, B12, B13, B14, B15,
    B16, B17, B18, B19, B20, B21, B22, B23,
    B24, B25, B26, B27, B28, B29, B30, B31,
    B32, B33, B34, B35, B36, B37, B38, B39,
    B40, B41, B42, B43, B44, B45, B46, B47,
    B48, B49, B50, B51, B52, B53, B54, B55,
    B56, B57, B58, B59, B60, B61, B62, B63
}

private enum class LargeSetEnum {
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

class EnumSetTest {
    @Test
    fun fullEnumSetOfCreatesAllEntries() {
        val set = fullEnumSetOf<SmallSetEnum>()
        assertEquals(SmallSetEnum.entries.size, set.size)
        assertTrue(set.containsAll(SmallSetEnum.entries))
    }

    @Test
    fun i32SetOperations() {
        val a = enumSetOf(SmallSetEnum.A0, SmallSetEnum.A2, SmallSetEnum.A4, SmallSetEnum.A6)
        val b = enumSetOf(SmallSetEnum.A2, SmallSetEnum.A3, SmallSetEnum.A4)

        assertTrue(a.containsAny(b))

        val intersect = a.intersect(b)
        assertEquals(setOf(SmallSetEnum.A2, SmallSetEnum.A4), intersect)

        val union = a.union(b)
        assertEquals(setOf(SmallSetEnum.A0, SmallSetEnum.A2, SmallSetEnum.A3, SmallSetEnum.A4, SmallSetEnum.A6), union)

        val diff = a.difference(b)
        assertEquals(setOf(SmallSetEnum.A0, SmallSetEnum.A6), diff)
    }

    @Test
    fun i64MutableSetMutations() {
        val set = mutableEnumSetOf<MidSetEnum>()
        assertTrue(set.isEmpty())

        assertTrue(set.add(MidSetEnum.B63))
        assertTrue(set.add(MidSetEnum.B10))
        assertFalse(set.add(MidSetEnum.B10))

        assertTrue(set.contains(MidSetEnum.B63))
        assertEquals(2, set.size)

        assertTrue(set.remove(MidSetEnum.B10))
        assertFalse(set.remove(MidSetEnum.B10))
        assertEquals(1, set.size)
    }

    @Test
    fun largeSetOperationsAndTrim() {
        val set = mutableEnumSetOf<LargeSetEnum>()
        set.add(LargeSetEnum.C95)
        set.add(LargeSetEnum.C00)
        assertEquals(2, set.size)

        assertTrue(set.remove(LargeSetEnum.C95))
        assertTrue(set.contains(LargeSetEnum.C00))
        assertFalse(set.contains(LargeSetEnum.C95))

        val other = enumSetOf(LargeSetEnum.C00, LargeSetEnum.C01)
        val diff = set.difference(other)
        assertTrue(diff.isEmpty())
    }

    @Test
    fun fallbackAgainstPlainSet() {
        val set = enumSetOf(SmallSetEnum.A1, SmallSetEnum.A2, SmallSetEnum.A3)
        val plain = linkedSetOf(SmallSetEnum.A2, SmallSetEnum.A3, SmallSetEnum.A4)

        assertTrue(set.containsAny(plain))
        assertEquals(setOf(SmallSetEnum.A2, SmallSetEnum.A3), set.intersect(plain))
        assertEquals(setOf(SmallSetEnum.A1), set.difference(plain))
    }

    @Test
    fun toEnumSetFromMutableCreatesSnapshot() {
        val mutable = mutableEnumSetOf(SmallSetEnum.A1, SmallSetEnum.A2)
        val immutable = mutable.toEnumSet()

        mutable.add(SmallSetEnum.A3)
        mutable.remove(SmallSetEnum.A1)

        assertEquals(setOf(SmallSetEnum.A1, SmallSetEnum.A2), immutable)
        assertFalse(immutable.contains(SmallSetEnum.A3))
    }

    @Test
    fun toMutableEnumSetFromImmutableCreatesCopy() {
        val immutable = enumSetOf(SmallSetEnum.A5, SmallSetEnum.A6)
        val copy = immutable.toMutableEnumSet()

        copy.add(SmallSetEnum.A7)
        copy.remove(SmallSetEnum.A5)

        assertEquals(setOf(SmallSetEnum.A5, SmallSetEnum.A6), immutable)
        assertEquals(setOf(SmallSetEnum.A6, SmallSetEnum.A7), copy)
    }

    @Test
    fun toMutableEnumSetFromMutableCreatesIndependentCopy() {
        val mutable = mutableEnumSetOf(SmallSetEnum.A11, SmallSetEnum.A12)
        val copy = mutable.toMutableEnumSet()

        mutable.add(SmallSetEnum.A13)
        copy.remove(SmallSetEnum.A11)

        assertEquals(setOf(SmallSetEnum.A11, SmallSetEnum.A12, SmallSetEnum.A13), mutable)
        assertEquals(setOf(SmallSetEnum.A12), copy)
    }

    @Test
    fun mutableCopyIsIndependent() {
        val original = mutableEnumSetOf(SmallSetEnum.A1, SmallSetEnum.A2)
        val copy = original.copy()

        assertFalse(copy === original)

        copy.add(SmallSetEnum.A3)
        original.remove(SmallSetEnum.A1)

        assertEquals(setOf(SmallSetEnum.A2), original)
        assertEquals(setOf(SmallSetEnum.A1, SmallSetEnum.A2, SmallSetEnum.A3), copy)
    }

    @Test
    fun mutableLargeCopyIsDeep() {
        val original = mutableEnumSetOf<LargeSetEnum>()
        original.add(LargeSetEnum.C00)
        original.add(LargeSetEnum.C95)

        val copy = original.copy()
        copy.remove(LargeSetEnum.C95)
        copy.add(LargeSetEnum.C01)

        assertTrue(original.contains(LargeSetEnum.C95))
        assertFalse(original.contains(LargeSetEnum.C01))
        assertEquals(setOf(LargeSetEnum.C00, LargeSetEnum.C95), original)
        assertEquals(setOf(LargeSetEnum.C00, LargeSetEnum.C01), copy)
    }

    @Test
    fun plainSetConversionCreatesIndependentMutableCopy() {
        val plain = linkedSetOf(SmallSetEnum.A8, SmallSetEnum.A9)
        val mutable = plain.toMutableEnumSet()
        mutable.add(SmallSetEnum.A10)

        assertEquals(setOf(SmallSetEnum.A8, SmallSetEnum.A9), plain)
        assertEquals(setOf(SmallSetEnum.A8, SmallSetEnum.A9, SmallSetEnum.A10), mutable)
    }
}
