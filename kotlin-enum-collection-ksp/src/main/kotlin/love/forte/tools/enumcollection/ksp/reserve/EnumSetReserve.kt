package love.forte.tools.enumcollection.ksp.reserve

import com.google.devtools.ksp.symbol.KSFile
import love.forte.codegentle.common.code.CodeValue
import love.forte.codegentle.common.code.beginControlFlow
import love.forte.codegentle.common.code.endControlFlow
import love.forte.codegentle.common.code.nextControlFlow
import love.forte.codegentle.common.naming.ClassName
import love.forte.codegentle.common.naming.canonicalName
import love.forte.codegentle.common.naming.parameterized
import love.forte.codegentle.common.naming.simpleNames
import love.forte.codegentle.common.ref.TypeRef
import love.forte.codegentle.common.ref.ref
import love.forte.codegentle.kotlin.KotlinModifier
import love.forte.codegentle.kotlin.naming.KotlinClassNames
import love.forte.codegentle.kotlin.ref.kotlinStatus
import love.forte.codegentle.kotlin.spec.KotlinConstructorSpec
import love.forte.codegentle.kotlin.spec.KotlinFunctionSpec
import love.forte.codegentle.kotlin.spec.KotlinObjectTypeSpec
import love.forte.codegentle.kotlin.spec.KotlinPropertySpec
import love.forte.codegentle.kotlin.spec.KotlinSimpleTypeSpec
import love.forte.codegentle.kotlin.spec.KotlinTypeSpec
import love.forte.codegentle.kotlin.spec.KotlinValueParameterSpec
import love.forte.codegentle.kotlin.spec.getter
import love.forte.codegentle.kotlin.spec.immutableProperty
import love.forte.tools.enumcollection.ksp.EnumDetail
import love.forte.tools.enumcollection.ksp.configuration.InheritanceMode

/**
 * Reserve for generating a concrete `XxxEnumSet` type.
 *
 * The generated implementation is optimized based on the enum entry count:
 * - `Int` bitset for <= 32 entries
 * - `Long` bitset for <= 64 entries
 * - `LongArray` bitset for larger enums
 *
 * @author Forte Scarlet
 */
internal class EnumSetReserve(
    override val sources: Set<KSFile>,
    override val targetName: String,
    override val visibility: String,
    override val enumDetail: EnumDetail,
) : EnumCollectionReserve() {

    override val apiInheritanceTypeQualifiedName: String = API_ENUM_SET.canonicalName

    override fun generateType(inheritanceAvailable: Boolean, inheritanceMode: InheritanceMode): KotlinTypeSpec {
        val inheritApi = shouldInheritApiType(inheritanceMode, inheritanceAvailable)
        val enumType = ClassName(enumDetail.qualifiedName)
        val enumRef = enumType.simpleNames.joinToString(".")
        val entrySize = enumDetail.elements.size

        return when {
            entrySize <= Int.SIZE_BITS -> generateI32EnumSetType(enumType, enumRef, inheritApi)
            entrySize <= Long.SIZE_BITS -> generateI64EnumSetType(enumType, enumRef, inheritApi)
            else -> generateLargeEnumSetType(enumType, enumRef, inheritApi)
        }
    }

    private fun generateI32EnumSetType(enumType: ClassName, enumRef: String, inheritApi: Boolean): KotlinTypeSpec {
        val elementTypeRef = enumType.ref()
        val collectionTypeRef = KotlinClassNames.COLLECTION.parameterized(elementTypeRef).ref()
        val setType = KotlinClassNames.SET.parameterized(elementTypeRef)
        val setTypeRef = setType.ref()
        val iteratorTypeRef = ClassName("kotlin.collections", "Iterator").parameterized(elementTypeRef).ref()
        val superInterface = if (inheritApi) API_ENUM_SET.parameterized(elementTypeRef) else setType

        val selfClassName = ClassName(enumDetail.packageName.ifBlank { null }, targetName)
        val selfTypeRef = selfClassName.ref()

        val entrySize = enumDetail.elements.size
        val fullBits = when {
            entrySize == 0 -> 0
            entrySize == Int.SIZE_BITS -> -1
            else -> (1 shl entrySize) - 1
        }

        return KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, targetName) {
            applyVisibility(visibility)
            addDoc("An immutable enum set optimized for [$enumRef].")

            primaryConstructor(
                KotlinConstructorSpec {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(
                        KotlinValueParameterSpec("bs", KotlinClassNames.INT.ref()) {
                            addModifier(KotlinModifier.PRIVATE)
                            immutableProperty()
                        }
                    )
                }
            )

            addSuperinterface(superInterface)

            addProperty(
                KotlinPropertySpec("size", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    getter { addCode("return bs.countOneBits()") }
                }
            )

            addFunction(
                KotlinFunctionSpec("contains", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("element", elementTypeRef))
                    addStatement("return (bs and (1 shl element.ordinal)) != 0")
                }
            )

            addFunction(
                KotlinFunctionSpec("containsAll", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("elements", collectionTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (elements.isEmpty()) return true")
                            addStatement("val currentBits = bs")
                            addStatement("if (currentBits == 0) return false")

                            beginControlFlow("if (elements is $targetName)")
                            addStatement("val otherBits = elements.bs")
                            addStatement("return (currentBits and otherBits) == otherBits")
                            endControlFlow()

                            addStatement("if (elements is Set<*> && elements.size > currentBits.countOneBits()) return false")

                            beginControlFlow("for (element in elements)")
                            addStatement("if ((currentBits and (1 shl element.ordinal)) == 0) return false")
                            endControlFlow()

                            addStatement("return true")
                        }
                    )
                }
            )

            // Extra API from love.forte.tools.enumcollection.api.EnumSet.
            addFunction(
                KotlinFunctionSpec("containsAny", KotlinClassNames.BOOLEAN.ref()) {
                    overrideIf(inheritApi)
                    addParameter(KotlinValueParameterSpec("elements", collectionTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (elements.isEmpty()) return false")
                            addStatement("val currentBits = bs")
                            addStatement("if (currentBits == 0) return false")

                            beginControlFlow("if (elements is $targetName)")
                            addStatement("return (currentBits and elements.bs) != 0")
                            endControlFlow()

                            beginControlFlow("for (element in elements)")
                            addStatement("if ((currentBits and (1 shl element.ordinal)) != 0) return true")
                            endControlFlow()

                            addStatement("return false")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("intersect", setTypeRef) {
                    overrideIf(inheritApi)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val currentBits = bs")
                            addStatement("if (currentBits == 0 || other.isEmpty()) return empty()")
                            addStatement("if (other === this) return this")

                            beginControlFlow("if (other is $targetName)")
                            addStatement("val intersectBits = currentBits and other.bs")
                            addStatement("if (intersectBits == currentBits) return this")
                            addStatement("return create(intersectBits)")
                            endControlFlow()

                            addStatement("var intersectBits = 0")
                            beginControlFlow("if (other.size < currentBits.countOneBits())")
                            beginControlFlow("for (element in other)")
                            addStatement("val mask = 1 shl element.ordinal")
                            beginControlFlow("if ((currentBits and mask) != 0)")
                            addStatement("intersectBits = intersectBits or mask")
                            endControlFlow()
                            endControlFlow()
                            nextControlFlow("else")
                            addStatement("var remaining = currentBits")
                            beginControlFlow("while (remaining != 0)")
                            addStatement("val bit = remaining.countTrailingZeroBits()")
                            beginControlFlow("if (other.contains($enumRef.entries[bit]))")
                            addStatement("intersectBits = intersectBits or (1 shl bit)")
                            endControlFlow()
                            addStatement("remaining = remaining and (remaining - 1)")
                            endControlFlow()
                            endControlFlow()

                            addStatement("if (intersectBits == currentBits) return this")
                            addStatement("return create(intersectBits)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("union", setTypeRef) {
                    overrideIf(inheritApi)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val currentBits = bs")
                            addStatement("if (other.isEmpty()) return this")
                            addStatement("if (other === this) return this")

                            beginControlFlow("if (other is $targetName)")
                            addStatement("val mergedBits = currentBits or other.bs")
                            addStatement("if (mergedBits == currentBits) return this")
                            addStatement("return create(mergedBits)")
                            endControlFlow()

                            addStatement("var mergedBits = currentBits")
                            beginControlFlow("for (element in other)")
                            addStatement("mergedBits = mergedBits or (1 shl element.ordinal)")
                            endControlFlow()

                            addStatement("if (mergedBits == currentBits) return this")
                            addStatement("return create(mergedBits)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("difference", setTypeRef) {
                    overrideIf(inheritApi)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val currentBits = bs")
                            addStatement("if (currentBits == 0 || other.isEmpty()) return this")
                            addStatement("if (other === this) return empty()")

                            beginControlFlow("if (other is $targetName)")
                            addStatement("val differenceBits = currentBits and other.bs.inv()")
                            addStatement("if (differenceBits == currentBits) return this")
                            addStatement("return create(differenceBits)")
                            endControlFlow()

                            addStatement("var removeMask = 0")
                            beginControlFlow("for (element in other)")
                            addStatement("removeMask = removeMask or (1 shl element.ordinal)")
                            endControlFlow()

                            addStatement("if (removeMask == 0) return this")
                            addStatement("val differenceBits = currentBits and removeMask.inv()")
                            addStatement("if (differenceBits == currentBits) return this")
                            addStatement("return create(differenceBits)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("isEmpty", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return bs == 0")
                }
            )

            addFunction(
                KotlinFunctionSpec("iterator", iteratorTypeRef) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addCode(
                        CodeValue {
                            beginControlFlow("return object : Iterator<$enumRef>")
                            addStatement("private var remaining = bs")

                            addStatement("override fun hasNext(): Boolean = remaining != 0")

                            beginControlFlow("override fun next(): $enumRef")
                            addStatement("val current = remaining")
                            addStatement("if (current == 0) throw NoSuchElementException()")
                            addStatement("val bit = current.countTrailingZeroBits()")
                            addStatement("remaining = current and (current - 1)")
                            addStatement("return $enumRef.entries[bit]")
                            endControlFlow()
                            endControlFlow()
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("toString", KotlinClassNames.STRING.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return joinToString(\", \", \"[\", \"]\")")
                }
            )

            addFunction(
                KotlinFunctionSpec("equals", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(
                        KotlinValueParameterSpec(
                            "other",
                            KotlinClassNames.ANY.ref { kotlinStatus { nullable = true } }
                        )
                    )
                    addCode(
                        CodeValue {
                            addStatement("if (this === other) return true")
                            addStatement("if (other !is Set<*>) return false")

                            beginControlFlow("if (other is $targetName)")
                            addStatement("return bs == other.bs")
                            endControlFlow()

                            addStatement("val currentBits = bs")
                            addStatement("if (other.size != currentBits.countOneBits()) return false")

                            beginControlFlow("for (element in other)")
                            addStatement("val e = element as? $enumRef ?: return false")
                            addStatement("val mask = 1 shl e.ordinal")
                            addStatement("if ((currentBits and mask) == 0) return false")
                            endControlFlow()

                            addStatement("return true")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("hashCode", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addCode(
                        CodeValue {
                            addStatement("var hash = 0")
                            addStatement("var remaining = bs")
                            beginControlFlow("while (remaining != 0)")
                            addStatement("val bit = remaining.countTrailingZeroBits()")
                            addStatement("hash += $enumRef.entries[bit].hashCode()")
                            addStatement("remaining = remaining and (remaining - 1)")
                            endControlFlow()
                            addStatement("return hash")
                        }
                    )
                }
            )

            addSubtype(generateI32CompanionObject(enumRef, elementTypeRef, selfTypeRef, fullBits))
        }
    }

    private fun generateI64EnumSetType(enumType: ClassName, enumRef: String, inheritApi: Boolean): KotlinTypeSpec {
        val elementTypeRef = enumType.ref()
        val collectionTypeRef = KotlinClassNames.COLLECTION.parameterized(elementTypeRef).ref()
        val setType = KotlinClassNames.SET.parameterized(elementTypeRef)
        val setTypeRef = setType.ref()
        val iteratorTypeRef = ClassName("kotlin.collections", "Iterator").parameterized(elementTypeRef).ref()
        val superInterface = if (inheritApi) API_ENUM_SET.parameterized(elementTypeRef) else setType

        val selfClassName = ClassName(enumDetail.packageName.ifBlank { null }, targetName)
        val selfTypeRef = selfClassName.ref()

        val entrySize = enumDetail.elements.size
        val fullBits = when {
            entrySize == 0 -> 0L
            entrySize == Long.SIZE_BITS -> -1L
            else -> (1L shl entrySize) - 1L
        }

        return KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, targetName) {
            applyVisibility(visibility)
            addDoc("An immutable enum set optimized for [$enumRef].")

            primaryConstructor(
                KotlinConstructorSpec {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(
                        KotlinValueParameterSpec("bs", KotlinClassNames.LONG.ref()) {
                            addModifier(KotlinModifier.PRIVATE)
                            immutableProperty()
                        }
                    )
                }
            )

            addSuperinterface(superInterface)

            addProperty(
                KotlinPropertySpec("size", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    getter { addCode("return bs.countOneBits()") }
                }
            )

            addFunction(
                KotlinFunctionSpec("contains", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("element", elementTypeRef))
                    addStatement("return (bs and (1L shl element.ordinal)) != 0L")
                }
            )

            addFunction(
                KotlinFunctionSpec("containsAll", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("elements", collectionTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (elements.isEmpty()) return true")
                            addStatement("val currentBits = bs")
                            addStatement("if (currentBits == 0L) return false")

                            beginControlFlow("if (elements is $targetName)")
                            addStatement("val otherBits = elements.bs")
                            addStatement("return (currentBits and otherBits) == otherBits")
                            endControlFlow()

                            addStatement("if (elements is Set<*> && elements.size > currentBits.countOneBits()) return false")

                            beginControlFlow("for (element in elements)")
                            addStatement("if ((currentBits and (1L shl element.ordinal)) == 0L) return false")
                            endControlFlow()

                            addStatement("return true")
                        }
                    )
                }
            )

            // Extra API from love.forte.tools.enumcollection.api.EnumSet.
            addFunction(
                KotlinFunctionSpec("containsAny", KotlinClassNames.BOOLEAN.ref()) {
                    overrideIf(inheritApi)
                    addParameter(KotlinValueParameterSpec("elements", collectionTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (elements.isEmpty()) return false")
                            addStatement("val currentBits = bs")
                            addStatement("if (currentBits == 0L) return false")

                            beginControlFlow("if (elements is $targetName)")
                            addStatement("return (currentBits and elements.bs) != 0L")
                            endControlFlow()

                            beginControlFlow("for (element in elements)")
                            addStatement("if ((currentBits and (1L shl element.ordinal)) != 0L) return true")
                            endControlFlow()

                            addStatement("return false")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("intersect", setTypeRef) {
                    overrideIf(inheritApi)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val currentBits = bs")
                            addStatement("if (currentBits == 0L || other.isEmpty()) return empty()")
                            addStatement("if (other === this) return this")

                            beginControlFlow("if (other is $targetName)")
                            addStatement("val intersectBits = currentBits and other.bs")
                            addStatement("if (intersectBits == currentBits) return this")
                            addStatement("return create(intersectBits)")
                            endControlFlow()

                            addStatement("var intersectBits = 0L")
                            beginControlFlow("if (other.size < currentBits.countOneBits())")
                            beginControlFlow("for (element in other)")
                            addStatement("val mask = 1L shl element.ordinal")
                            beginControlFlow("if ((currentBits and mask) != 0L)")
                            addStatement("intersectBits = intersectBits or mask")
                            endControlFlow()
                            endControlFlow()
                            nextControlFlow("else")
                            addStatement("var remaining = currentBits")
                            beginControlFlow("while (remaining != 0L)")
                            addStatement("val bit = remaining.countTrailingZeroBits()")
                            beginControlFlow("if (other.contains($enumRef.entries[bit]))")
                            addStatement("intersectBits = intersectBits or (1L shl bit)")
                            endControlFlow()
                            addStatement("remaining = remaining and (remaining - 1)")
                            endControlFlow()
                            endControlFlow()

                            addStatement("if (intersectBits == currentBits) return this")
                            addStatement("return create(intersectBits)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("union", setTypeRef) {
                    overrideIf(inheritApi)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val currentBits = bs")
                            addStatement("if (other.isEmpty()) return this")
                            addStatement("if (other === this) return this")

                            beginControlFlow("if (other is $targetName)")
                            addStatement("val mergedBits = currentBits or other.bs")
                            addStatement("if (mergedBits == currentBits) return this")
                            addStatement("return create(mergedBits)")
                            endControlFlow()

                            addStatement("var mergedBits = currentBits")
                            beginControlFlow("for (element in other)")
                            addStatement("mergedBits = mergedBits or (1L shl element.ordinal)")
                            endControlFlow()

                            addStatement("if (mergedBits == currentBits) return this")
                            addStatement("return create(mergedBits)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("difference", setTypeRef) {
                    overrideIf(inheritApi)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val currentBits = bs")
                            addStatement("if (currentBits == 0L || other.isEmpty()) return this")
                            addStatement("if (other === this) return empty()")

                            beginControlFlow("if (other is $targetName)")
                            addStatement("val differenceBits = currentBits and other.bs.inv()")
                            addStatement("if (differenceBits == currentBits) return this")
                            addStatement("return create(differenceBits)")
                            endControlFlow()

                            addStatement("var removeMask = 0L")
                            beginControlFlow("for (element in other)")
                            addStatement("removeMask = removeMask or (1L shl element.ordinal)")
                            endControlFlow()

                            addStatement("if (removeMask == 0L) return this")
                            addStatement("val differenceBits = currentBits and removeMask.inv()")
                            addStatement("if (differenceBits == currentBits) return this")
                            addStatement("return create(differenceBits)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("isEmpty", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return bs == 0L")
                }
            )

            addFunction(
                KotlinFunctionSpec("iterator", iteratorTypeRef) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addCode(
                        CodeValue {
                            beginControlFlow("return object : Iterator<$enumRef>")
                            addStatement("private var remaining = bs")

                            addStatement("override fun hasNext(): Boolean = remaining != 0L")

                            beginControlFlow("override fun next(): $enumRef")
                            addStatement("val current = remaining")
                            addStatement("if (current == 0L) throw NoSuchElementException()")
                            addStatement("val bit = current.countTrailingZeroBits()")
                            addStatement("remaining = current and (current - 1)")
                            addStatement("return $enumRef.entries[bit]")
                            endControlFlow()
                            endControlFlow()
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("toString", KotlinClassNames.STRING.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return joinToString(\", \", \"[\", \"]\")")
                }
            )

            addFunction(
                KotlinFunctionSpec("equals", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(
                        KotlinValueParameterSpec(
                            "other",
                            KotlinClassNames.ANY.ref { kotlinStatus { nullable = true } }
                        )
                    )
                    addCode(
                        CodeValue {
                            addStatement("if (this === other) return true")
                            addStatement("if (other !is Set<*>) return false")

                            beginControlFlow("if (other is $targetName)")
                            addStatement("return bs == other.bs")
                            endControlFlow()

                            addStatement("val currentBits = bs")
                            addStatement("if (other.size != currentBits.countOneBits()) return false")

                            beginControlFlow("for (element in other)")
                            addStatement("val e = element as? $enumRef ?: return false")
                            addStatement("val mask = 1L shl e.ordinal")
                            addStatement("if ((currentBits and mask) == 0L) return false")
                            endControlFlow()

                            addStatement("return true")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("hashCode", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addCode(
                        CodeValue {
                            addStatement("var hash = 0")
                            addStatement("var remaining = bs")
                            beginControlFlow("while (remaining != 0L)")
                            addStatement("val bit = remaining.countTrailingZeroBits()")
                            addStatement("hash += $enumRef.entries[bit].hashCode()")
                            addStatement("remaining = remaining and (remaining - 1)")
                            endControlFlow()
                            addStatement("return hash")
                        }
                    )
                }
            )

            addSubtype(generateI64CompanionObject(enumRef, elementTypeRef, selfTypeRef, fullBits))
        }
    }

    private fun generateLargeEnumSetType(enumType: ClassName, enumRef: String, inheritApi: Boolean): KotlinTypeSpec {
        val elementTypeRef = enumType.ref()
        val collectionTypeRef = KotlinClassNames.COLLECTION.parameterized(elementTypeRef).ref()
        val setType = KotlinClassNames.SET.parameterized(elementTypeRef)
        val setTypeRef = setType.ref()
        val iteratorTypeRef = ClassName("kotlin.collections", "Iterator").parameterized(elementTypeRef).ref()
        val superInterface = if (inheritApi) API_ENUM_SET.parameterized(elementTypeRef) else setType

        val selfClassName = ClassName(enumDetail.packageName.ifBlank { null }, targetName)
        val selfTypeRef = selfClassName.ref()

        val entrySize = enumDetail.elements.size
        val maxWordSize = (entrySize + 63) ushr 6

        return KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, targetName) {
            applyVisibility(visibility)
            addDoc("An immutable enum set optimized for [$enumRef].")

            primaryConstructor(
                KotlinConstructorSpec {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(
                        KotlinValueParameterSpec(
                            "bs",
                            ClassName("kotlin", "LongArray").ref()
                        ) {
                            addModifier(KotlinModifier.PRIVATE)
                            immutableProperty()
                        }
                    )
                }
            )

            addSuperinterface(superInterface)

            addProperty(
                KotlinPropertySpec("size", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    getter { addCode("return bitCountOf(bs)") }
                }
            )

            addFunction(
                KotlinFunctionSpec("contains", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("element", elementTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val ordinal = element.ordinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("return wordIndex < bs.size && (bs[wordIndex] and (1L shl (ordinal and 63))) != 0L")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("containsAll", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("elements", collectionTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (elements.isEmpty()) return true")
                            addStatement("if (bs.isEmpty()) return false")

                            beginControlFlow("if (elements is $targetName)")
                            addStatement("val otherWords = elements.bs")
                            addStatement("if (otherWords.size > bs.size) return false")
                            beginControlFlow("for (wordIndex in otherWords.indices)")
                            addStatement("val otherWord = otherWords[wordIndex]")
                            addStatement("if ((bs[wordIndex] and otherWord) != otherWord) return false")
                            endControlFlow()
                            addStatement("return true")
                            endControlFlow()

                            beginControlFlow("for (element in elements)")
                            addStatement("if (!contains(element)) return false")
                            endControlFlow()
                            addStatement("return true")
                        }
                    )
                }
            )

            // Extra API from love.forte.tools.enumcollection.api.EnumSet.
            addFunction(
                KotlinFunctionSpec("containsAny", KotlinClassNames.BOOLEAN.ref()) {
                    overrideIf(inheritApi)
                    addParameter(KotlinValueParameterSpec("elements", collectionTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (elements.isEmpty() || bs.isEmpty()) return false")

                            beginControlFlow("if (elements is $targetName)")
                            addStatement("val otherWords = elements.bs")
                            addStatement("val minSize = minOf(bs.size, otherWords.size)")
                            beginControlFlow("for (wordIndex in 0 until minSize)")
                            addStatement("if ((bs[wordIndex] and otherWords[wordIndex]) != 0L) return true")
                            endControlFlow()
                            addStatement("return false")
                            endControlFlow()

                            beginControlFlow("for (element in elements)")
                            addStatement("if (contains(element)) return true")
                            endControlFlow()
                            addStatement("return false")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("intersect", setTypeRef) {
                    overrideIf(inheritApi)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (bs.isEmpty() || other.isEmpty()) return empty()")
                            addStatement("if (other === this) return this")

                            beginControlFlow("if (other is $targetName)")
                            addStatement("val otherWords = other.bs")
                            addStatement("val minSize = minOf(bs.size, otherWords.size)")
                            addStatement("val resultWords = LongArray(minSize)")
                            addStatement("var lastNonZeroWordIndex = -1")
                            beginControlFlow("for (wordIndex in 0 until minSize)")
                            addStatement("val word = bs[wordIndex] and otherWords[wordIndex]")
                            addStatement("resultWords[wordIndex] = word")
                            addStatement("if (word != 0L) lastNonZeroWordIndex = wordIndex")
                            endControlFlow()
                            addStatement("return create(trimByLastNonZero(resultWords, lastNonZeroWordIndex))")
                            endControlFlow()

                            addStatement("val currentWords = bs")
                            addStatement("val resultWords = LongArray(currentWords.size)")
                            addStatement("var lastNonZeroWordIndex = -1")
                            addStatement("val currentSize = bitCountOf(currentWords)")

                            beginControlFlow("if (other.size < currentSize)")
                            beginControlFlow("for (element in other)")
                            addStatement("val ordinal = element.ordinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("if (wordIndex >= currentWords.size) continue")
                            addStatement("val bitMask = 1L shl (ordinal and 63)")
                            addStatement("if ((currentWords[wordIndex] and bitMask) == 0L) continue")
                            addStatement("resultWords[wordIndex] = resultWords[wordIndex] or bitMask")
                            addStatement("if (wordIndex > lastNonZeroWordIndex) lastNonZeroWordIndex = wordIndex")
                            endControlFlow()
                            nextControlFlow("else")
                            beginControlFlow("for (wordIndex in currentWords.indices)")
                            addStatement("var word = currentWords[wordIndex]")
                            beginControlFlow("while (word != 0L)")
                            addStatement("val bit = word.countTrailingZeroBits()")
                            beginControlFlow("if (other.contains($enumRef.entries[(wordIndex shl 6) + bit]))")
                            addStatement("resultWords[wordIndex] = resultWords[wordIndex] or (1L shl bit)")
                            addStatement("lastNonZeroWordIndex = wordIndex")
                            endControlFlow()
                            addStatement("word = word and (word - 1)")
                            endControlFlow()
                            endControlFlow()
                            endControlFlow()

                            addStatement("return create(trimByLastNonZero(resultWords, lastNonZeroWordIndex))")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("union", setTypeRef) {
                    overrideIf(inheritApi)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (other.isEmpty()) return this")
                            addStatement("if (other === this) return this")

                            beginControlFlow("if (other is $targetName)")
                            addStatement("val currentWords = bs")
                            addStatement("val otherWords = other.bs")
                            beginControlFlow("if (otherWords.size <= currentWords.size)")
                            addStatement("val resultWords = currentWords.copyOf()")
                            addStatement("var changed = false")
                            beginControlFlow("for (wordIndex in otherWords.indices)")
                            addStatement("val oldWord = resultWords[wordIndex]")
                            addStatement("val mergedWord = oldWord or otherWords[wordIndex]")
                            beginControlFlow("if (mergedWord != oldWord)")
                            addStatement("resultWords[wordIndex] = mergedWord")
                            addStatement("changed = true")
                            endControlFlow()
                            endControlFlow()
                            addStatement("if (!changed) return this")
                            addStatement("return create(resultWords)")
                            nextControlFlow("else")
                            addStatement("val resultWords = otherWords.copyOf()")
                            beginControlFlow("for (wordIndex in currentWords.indices)")
                            addStatement("resultWords[wordIndex] = resultWords[wordIndex] or currentWords[wordIndex]")
                            endControlFlow()
                            addStatement("return create(resultWords)")
                            endControlFlow()
                            endControlFlow()

                            addStatement("var resultWords = bs.copyOf()")
                            addStatement("var changed = false")
                            beginControlFlow("for (element in other)")
                            addStatement("val ordinal = element.ordinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            beginControlFlow("if (wordIndex >= resultWords.size)")
                            addStatement("resultWords = resultWords.copyOf(wordIndex + 1)")
                            addStatement("changed = true")
                            endControlFlow()
                            addStatement("val oldWord = resultWords[wordIndex]")
                            addStatement("val newWord = oldWord or (1L shl (ordinal and 63))")
                            beginControlFlow("if (newWord != oldWord)")
                            addStatement("resultWords[wordIndex] = newWord")
                            addStatement("changed = true")
                            endControlFlow()
                            endControlFlow()

                            addStatement("if (!changed) return this")
                            addStatement("return create(resultWords)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("difference", setTypeRef) {
                    overrideIf(inheritApi)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (bs.isEmpty() || other.isEmpty()) return this")
                            addStatement("if (other === this) return empty()")

                            beginControlFlow("if (other is $targetName)")
                            addStatement("val otherWords = other.bs")
                            addStatement("val resultWords = bs.copyOf()")
                            addStatement("var changed = false")
                            addStatement("val minSize = minOf(resultWords.size, otherWords.size)")
                            beginControlFlow("for (wordIndex in 0 until minSize)")
                            addStatement("val oldWord = resultWords[wordIndex]")
                            addStatement("val newWord = oldWord and otherWords[wordIndex].inv()")
                            beginControlFlow("if (newWord != oldWord)")
                            addStatement("resultWords[wordIndex] = newWord")
                            addStatement("changed = true")
                            endControlFlow()
                            endControlFlow()
                            addStatement("if (!changed) return this")
                            addStatement("return create(resultWords)")
                            endControlFlow()

                            addStatement("val resultWords = bs.copyOf()")
                            addStatement("var removed = false")
                            beginControlFlow("for (element in other)")
                            addStatement("val ordinal = element.ordinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("if (wordIndex >= resultWords.size) continue")
                            addStatement("val oldWord = resultWords[wordIndex]")
                            addStatement("val newWord = oldWord and (1L shl (ordinal and 63)).inv()")
                            beginControlFlow("if (newWord != oldWord)")
                            addStatement("removed = true")
                            addStatement("resultWords[wordIndex] = newWord")
                            endControlFlow()
                            endControlFlow()

                            addStatement("if (!removed) return this")
                            addStatement("return create(resultWords)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("isEmpty", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return bs.isEmpty()")
                }
            )

            addFunction(
                KotlinFunctionSpec("iterator", iteratorTypeRef) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addCode(
                        CodeValue {
                            beginControlFlow("return object : Iterator<$enumRef>")
                            addStatement("private var currentWordIndex = 0")
                            addStatement("private var currentWord = if (bs.isNotEmpty()) bs[0] else 0L")

                            beginControlFlow("override fun hasNext(): Boolean")
                            beginControlFlow("while (currentWord == 0L && currentWordIndex < bs.lastIndex)")
                            addStatement("currentWordIndex++")
                            addStatement("currentWord = bs[currentWordIndex]")
                            endControlFlow()
                            addStatement("return currentWord != 0L")
                            endControlFlow()

                            beginControlFlow("override fun next(): $enumRef")
                            addStatement("if (!hasNext()) throw NoSuchElementException()")
                            addStatement("val bit = currentWord.countTrailingZeroBits()")
                            addStatement("currentWord = currentWord and (currentWord - 1)")
                            addStatement("return $enumRef.entries[(currentWordIndex shl 6) + bit]")
                            endControlFlow()
                            endControlFlow()
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("toString", KotlinClassNames.STRING.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return joinToString(\", \", \"[\", \"]\")")
                }
            )

            addFunction(
                KotlinFunctionSpec("equals", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(
                        KotlinValueParameterSpec(
                            "other",
                            KotlinClassNames.ANY.ref { kotlinStatus { nullable = true } }
                        )
                    )
                    addCode(
                        CodeValue {
                            addStatement("if (this === other) return true")
                            addStatement("if (other !is Set<*>) return false")

                            beginControlFlow("if (other is $targetName)")
                            addStatement("return bs.contentEquals(other.bs)")
                            endControlFlow()

                            addStatement("if (other.size != size) return false")
                            beginControlFlow("for (element in other)")
                            addStatement("val e = element as? $enumRef ?: return false")
                            addStatement("if (!contains(e)) return false")
                            endControlFlow()
                            addStatement("return true")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("hashCode", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addCode(
                        CodeValue {
                            addStatement("var hash = 0")
                            beginControlFlow("for (wordIndex in bs.indices)")
                            addStatement("var word = bs[wordIndex]")
                            beginControlFlow("while (word != 0L)")
                            addStatement("val bit = word.countTrailingZeroBits()")
                            addStatement("hash += $enumRef.entries[(wordIndex shl 6) + bit].hashCode()")
                            addStatement("word = word and (word - 1)")
                            endControlFlow()
                            endControlFlow()
                            addStatement("return hash")
                        }
                    )
                }
            )

            addSubtype(generateLargeCompanionObject(enumRef, elementTypeRef, selfTypeRef, entrySize, maxWordSize))
        }
    }

    private fun generateI32CompanionObject(
        enumRef: String,
        enumTypeRef: TypeRef<*>,
        selfTypeRef: TypeRef<*>,
        fullBits: Int
    ): KotlinObjectTypeSpec = KotlinObjectTypeSpec {
        addProperty(
            KotlinPropertySpec("EMPTY", selfTypeRef) {
                addModifier(KotlinModifier.PRIVATE)
                initializer("$targetName(0)")
            }
        )

        addFunction(
            KotlinFunctionSpec("create", selfTypeRef) {
                addModifier(KotlinModifier.PRIVATE)
                addParameter(KotlinValueParameterSpec("bits", KotlinClassNames.INT.ref()))
                addStatement("return if (bits == 0) EMPTY else $targetName(bits)")
            }
        )

        addProperty(
            KotlinPropertySpec("FULL", selfTypeRef) {
                addModifier(KotlinModifier.PRIVATE)
                initializer("create($fullBits)")
            }
        )

        addFunction(
            KotlinFunctionSpec("empty", selfTypeRef) {
                addStatement("return EMPTY")
            }
        )

        addFunction(
            KotlinFunctionSpec("full", selfTypeRef) {
                addStatement("return FULL")
            }
        )

        addFunction(
            KotlinFunctionSpec("of", selfTypeRef) {
                addParameter(
                    KotlinValueParameterSpec("elements", enumTypeRef) {
                        addModifier(KotlinModifier.VARARG)
                    }
                )
                addCode(
                    CodeValue {
                        addStatement("if (elements.isEmpty()) return EMPTY")
                        addStatement("var bits = 0")
                        beginControlFlow("for (element in elements)")
                        addStatement("bits = bits or (1 shl element.ordinal)")
                        endControlFlow()
                        addStatement("return create(bits)")
                    }
                )
            }
        )
    }

    private fun generateI64CompanionObject(
        enumRef: String,
        enumTypeRef: TypeRef<*>,
        selfTypeRef: TypeRef<*>,
        fullBits: Long
    ): KotlinObjectTypeSpec = KotlinObjectTypeSpec {
        addProperty(
            KotlinPropertySpec("EMPTY", selfTypeRef) {
                addModifier(KotlinModifier.PRIVATE)
                initializer("$targetName(0L)")
            }
        )

        addFunction(
            KotlinFunctionSpec("create", selfTypeRef) {
                addModifier(KotlinModifier.PRIVATE)
                addParameter(KotlinValueParameterSpec("bits", KotlinClassNames.LONG.ref()))
                addStatement("return if (bits == 0L) EMPTY else $targetName(bits)")
            }
        )

        addProperty(
            KotlinPropertySpec("FULL", selfTypeRef) {
                addModifier(KotlinModifier.PRIVATE)
                initializer("create($fullBits)")
            }
        )

        addFunction(
            KotlinFunctionSpec("empty", selfTypeRef) {
                addStatement("return EMPTY")
            }
        )

        addFunction(
            KotlinFunctionSpec("full", selfTypeRef) {
                addStatement("return FULL")
            }
        )

        addFunction(
            KotlinFunctionSpec("of", selfTypeRef) {
                addParameter(
                    KotlinValueParameterSpec("elements", enumTypeRef) {
                        addModifier(KotlinModifier.VARARG)
                    }
                )
                addCode(
                    CodeValue {
                        addStatement("if (elements.isEmpty()) return EMPTY")
                        addStatement("var bits = 0L")
                        beginControlFlow("for (element in elements)")
                        addStatement("bits = bits or (1L shl element.ordinal)")
                        endControlFlow()
                        addStatement("return create(bits)")
                    }
                )
            }
        )
    }

    private fun generateLargeCompanionObject(
        enumRef: String,
        enumTypeRef: TypeRef<*>,
        selfTypeRef: TypeRef<*>,
        entrySize: Int,
        maxWordSize: Int,
    ): KotlinObjectTypeSpec = KotlinObjectTypeSpec {
        addProperty(
            KotlinPropertySpec("EMPTY", selfTypeRef) {
                addModifier(KotlinModifier.PRIVATE)
                initializer("$targetName(LongArray(0))")
            }
        )

        // Helpers are placed in companion to keep the generated type lean.
        addFunction(
            KotlinFunctionSpec("lastNonZeroIndex", KotlinClassNames.INT.ref()) {
                addModifier(KotlinModifier.PRIVATE)
                addParameter(KotlinValueParameterSpec("words", ClassName("kotlin", "LongArray").ref()))
                addCode(
                    CodeValue {
                        addStatement("var index = words.size - 1")
                        beginControlFlow("while (index >= 0 && words[index] == 0L)")
                        addStatement("index--")
                        endControlFlow()
                        addStatement("return index")
                    }
                )
            }
        )

        addFunction(
            KotlinFunctionSpec("trimByLastNonZero", ClassName("kotlin", "LongArray").ref()) {
                addModifier(KotlinModifier.PRIVATE)
                addParameter(KotlinValueParameterSpec("words", ClassName("kotlin", "LongArray").ref()))
                addParameter(KotlinValueParameterSpec("lastNonZeroWordIndex", KotlinClassNames.INT.ref()))
                addCode(
                    CodeValue {
                        beginControlFlow("return when")
                        addStatement("lastNonZeroWordIndex < 0 -> LongArray(0)")
                        addStatement("lastNonZeroWordIndex == words.lastIndex -> words")
                        addStatement("else -> words.copyOf(lastNonZeroWordIndex + 1)")
                        endControlFlow()
                    }
                )
            }
        )

        addFunction(
            KotlinFunctionSpec("bitCountOf", KotlinClassNames.INT.ref()) {
                addModifier(KotlinModifier.PRIVATE)
                addParameter(KotlinValueParameterSpec("words", ClassName("kotlin", "LongArray").ref()))
                addCode(
                    CodeValue {
                        addStatement("var count = 0")
                        beginControlFlow("for (word in words)")
                        addStatement("count += word.countOneBits()")
                        endControlFlow()
                        addStatement("return count")
                    }
                )
            }
        )

        addFunction(
            KotlinFunctionSpec("create", selfTypeRef) {
                addModifier(KotlinModifier.PRIVATE)
                addParameter(KotlinValueParameterSpec("words", ClassName("kotlin", "LongArray").ref()))
                addCode(
                    CodeValue {
                        addStatement("val last = lastNonZeroIndex(words)")
                        addStatement("if (last < 0) return EMPTY")
                        addStatement("return $targetName(trimByLastNonZero(words, last))")
                    }
                )
            }
        )

        addProperty(
            KotlinPropertySpec("FULL", selfTypeRef) {
                addModifier(KotlinModifier.PRIVATE)
                initializer(
                    "run {" +
                        " if ($entrySize == 0) return@run EMPTY;" +
                        " val wordSize = $maxWordSize;" +
                        " val words = LongArray(wordSize) { -1L };" +
                        " val tail = $entrySize and 63;" +
                        " if (tail != 0) words[wordSize - 1] = (1L shl tail) - 1L;" +
                        " create(words)" +
                        " }"
                )
            }
        )

        addFunction(
            KotlinFunctionSpec("empty", selfTypeRef) {
                addStatement("return EMPTY")
            }
        )

        addFunction(
            KotlinFunctionSpec("full", selfTypeRef) {
                addStatement("return FULL")
            }
        )

        addFunction(
            KotlinFunctionSpec("of", selfTypeRef) {
                addParameter(
                    KotlinValueParameterSpec("elements", enumTypeRef) {
                        addModifier(KotlinModifier.VARARG)
                    }
                )
                addCode(
                    CodeValue {
                        addStatement("if (elements.isEmpty()) return EMPTY")
                        addStatement("var maxOrdinal = 0")
                        beginControlFlow("for (element in elements)")
                        addStatement("val ordinal = element.ordinal")
                        beginControlFlow("if (ordinal > maxOrdinal)")
                        addStatement("maxOrdinal = ordinal")
                        endControlFlow()
                        addStatement("if (maxOrdinal == $entrySize - 1) break")
                        endControlFlow()

                        addStatement("val wordSize = minOf($maxWordSize, (maxOrdinal + 64) ushr 6)")
                        addStatement("val words = LongArray(wordSize)")
                        beginControlFlow("for (element in elements)")
                        addStatement("val ordinal = element.ordinal")
                        addStatement("val wordIndex = ordinal ushr 6")
                        addStatement("words[wordIndex] = words[wordIndex] or (1L shl (ordinal and 63))")
                        endControlFlow()

                        addStatement("return create(words)")
                    }
                )
            }
        )
    }
}
