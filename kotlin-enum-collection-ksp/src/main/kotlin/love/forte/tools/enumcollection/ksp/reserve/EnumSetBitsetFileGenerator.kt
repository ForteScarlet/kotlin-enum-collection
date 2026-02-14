package love.forte.tools.enumcollection.ksp.reserve

import love.forte.codegentle.common.code.CodeValue
import love.forte.codegentle.common.code.beginControlFlow
import love.forte.codegentle.common.code.endControlFlow
import love.forte.codegentle.common.naming.ClassName
import love.forte.codegentle.common.naming.parameterized
import love.forte.codegentle.common.ref.ref
import love.forte.codegentle.kotlin.KotlinModifier
import love.forte.codegentle.kotlin.naming.KotlinClassNames
import love.forte.codegentle.kotlin.ref.kotlinStatus
import love.forte.codegentle.kotlin.spec.KotlinConstructorSpec
import love.forte.codegentle.kotlin.spec.KotlinFunctionSpec
import love.forte.codegentle.kotlin.spec.KotlinPropertySpec
import love.forte.codegentle.kotlin.spec.KotlinSimpleTypeSpec
import love.forte.codegentle.kotlin.spec.KotlinTypeSpec
import love.forte.codegentle.kotlin.spec.KotlinValueParameterSpec
import love.forte.codegentle.kotlin.spec.getter
import love.forte.codegentle.kotlin.spec.immutableProperty
import love.forte.codegentle.kotlin.spec.mutableProperty
import love.forte.tools.enumcollection.ksp.EnumDetail

/**
 * Generates bitset-based enum set implementations.
 *
 * Storage is either `Int` (<= 32 entries) or `Long` (<= 64 entries).
 */
internal object EnumSetBitsetFileGenerator {

    internal enum class BitsetKind {
        I32,
        I64,
    }

    internal fun generateFileSpec(
        enumDetail: EnumDetail,
        targetName: String,
        visibility: String,
        enumType: ClassName,
        enumRef: String,
        enumSize: Int,
        inheritApi: Boolean,
        bitsetKind: BitsetKind,
    ): FileSpec {
        val packageName = enumDetail.packageName.ifBlank { null }
        val keyTypeRef = enumType.ref()

        val immutableInterfaceName = targetName
        val mutableInterfaceName = "Mutable$targetName"
        val implName = "${targetName}Impl"
        val mutableImplName = "Mutable${targetName}Impl"

        val immutableType = ClassName(packageName, immutableInterfaceName)
        val mutableType = ClassName(packageName, mutableInterfaceName)

        val setType = KotlinClassNames.SET.parameterized(keyTypeRef)
        val mutableSetType = KotlinClassNames.MUTABLE_SET.parameterized(keyTypeRef)
        val collectionTypeRef = KotlinClassNames.COLLECTION.parameterized(keyTypeRef).ref()
        val setTypeRef = setType.ref()

        val superImmutable = if (inheritApi) API_ENUM_SET.parameterized(keyTypeRef) else setType
        val superMutable = if (inheritApi) API_MUTABLE_ENUM_SET.parameterized(keyTypeRef) else mutableSetType

        val bitsetTypeRef = when (bitsetKind) {
            BitsetKind.I32 -> KotlinClassNames.INT.ref()
            BitsetKind.I64 -> KotlinClassNames.LONG.ref()
        }

        val fullBitsLiteral = when (bitsetKind) {
            BitsetKind.I32 -> when {
                enumSize <= 0 -> "0"
                enumSize == Int.SIZE_BITS -> "-1"
                else -> ((1 shl enumSize) - 1).toString()
            }

            BitsetKind.I64 -> when {
                enumSize <= 0 -> "0L"
                enumSize == Long.SIZE_BITS -> "-1L"
                else -> ((1L shl enumSize) - 1L).toString() + "L"
            }
        }

        val privateBitsetInterface = KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.INTERFACE, "BitsetBased") {
            addModifier(KotlinModifier.PRIVATE)
            addProperty(KotlinPropertySpec("bs", bitsetTypeRef))
        }
        val bitsetBasedType = ClassName(packageName, "BitsetBased")

        val immutableInterface = KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.INTERFACE, immutableInterfaceName) {
            applyVisibility(visibility)
            addDoc("An enum-specialized set optimized for [$enumRef].")
            addSuperinterface(superImmutable)
            if (!inheritApi) {
                addFunction(
                    KotlinFunctionSpec("containsAny", KotlinClassNames.BOOLEAN.ref()) {
                        addParameter(KotlinValueParameterSpec("elements", collectionTypeRef))
                    }
                )
                addFunction(
                    KotlinFunctionSpec("intersect", setTypeRef) {
                        addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    }
                )
                addFunction(
                    KotlinFunctionSpec("union", setTypeRef) {
                        addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    }
                )
                addFunction(
                    KotlinFunctionSpec("difference", setTypeRef) {
                        addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    }
                )
            }
        }

        val mutableInterface = KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.INTERFACE, mutableInterfaceName) {
            applyVisibility(visibility)
            addDoc("A mutable variant of [$immutableInterfaceName].")
            addSuperinterface(immutableType)
            addSuperinterface(superMutable)
            addFunction(
                KotlinFunctionSpec("copy", mutableType.ref()) {
                    overrideIf(inheritApi)
                }
            )
        }

        val emptyProperty = KotlinPropertySpec("EMPTY", immutableType.ref()) {
            addModifier(KotlinModifier.PRIVATE)
            initializer("$implName(0${if (bitsetKind == BitsetKind.I64) "L" else ""})")
        }

        val fullBitsProperty = KotlinPropertySpec("FULL_BITS", bitsetTypeRef) {
            addModifier(KotlinModifier.PRIVATE)
            addModifier(KotlinModifier.CONST)
            initializer(fullBitsLiteral)
        }

        val fullProperty = KotlinPropertySpec("FULL", immutableType.ref()) {
            addModifier(KotlinModifier.PRIVATE)
            initializer("$implName(FULL_BITS)")
        }

        val createImmutableFunction = KotlinFunctionSpec("createEnumSet", immutableType.ref()) {
            addModifier(KotlinModifier.PRIVATE)
            addParameter(KotlinValueParameterSpec("bits", bitsetTypeRef))
            addCode(
                CodeValue {
                    addStatement("if (bits == 0${if (bitsetKind == BitsetKind.I64) "L" else ""}) return EMPTY")
                    addStatement("if (bits == FULL_BITS) return FULL")
                    addStatement("return $implName(bits)")
                }
            )
        }

        val utilToMutable = KotlinFunctionSpec("mutable", mutableType.ref()) {
            applyVisibility(visibility)
            receiver(immutableType.ref())
            addDoc("Returns an independent mutable copy of this set.")
            addCode(
                CodeValue {
                    beginControlFlow("return when (this)")
                    addStatement("is $mutableInterfaceName -> copy()")
                    addStatement("is $implName -> $mutableImplName(bs)")
                    addStatement("else -> $mutableInterfaceName(this)")
                    endControlFlow()
                }
            )
        }

        val utilToImmutable = KotlinFunctionSpec("immutable", immutableType.ref()) {
            applyVisibility(visibility)
            receiver(mutableType.ref())
            addDoc("Returns an immutable snapshot of this mutable set.")
            addCode(
                CodeValue {
                    beginControlFlow("return when (this)")
                    addStatement("is $mutableImplName -> createEnumSet(bs)")
                    addStatement("else -> $immutableInterfaceName(this)")
                    endControlFlow()
                }
            )
        }

        val factoryImmutableEmptyOrFull = KotlinFunctionSpec(immutableInterfaceName, immutableType.ref()) {
            applyVisibility(visibility)
            addParameter(
                KotlinValueParameterSpec("full", KotlinClassNames.BOOLEAN.ref()) {
                    defaultValue("false")
                }
            )
            addDoc("Creates an immutable empty or full set for [$enumRef].")
            addStatement("return if (full) FULL else EMPTY")
        }

        val factoryImmutableVararg = KotlinFunctionSpec(immutableInterfaceName, immutableType.ref()) {
            applyVisibility(visibility)
            addParameter(
                KotlinValueParameterSpec("entries", keyTypeRef) {
                    addModifier(KotlinModifier.VARARG)
                }
            )
            addDoc("Creates an immutable set containing [entries].")
            addCode(
                CodeValue {
                    addStatement("if (entries.isEmpty()) return EMPTY")
                    addStatement("var bits = 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                    beginControlFlow("for (entry in entries)")
                    addStatement(
                        "bits = bits or (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl entry.ordinal)"
                    )
                    endControlFlow()
                    addStatement("return createEnumSet(bits)")
                }
            )
        }

        val iterableTypeRef = KotlinClassNames.ITERABLE.parameterized(keyTypeRef).ref()
        val factoryImmutableIterable = KotlinFunctionSpec(immutableInterfaceName, immutableType.ref()) {
            applyVisibility(visibility)
            addParameter(KotlinValueParameterSpec("entries", iterableTypeRef))
            addDoc("Creates an immutable set containing [entries].")
            addCode(
                CodeValue {
                    addStatement("var bits = 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                    beginControlFlow("for (entry in entries)")
                    addStatement(
                        "bits = bits or (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl entry.ordinal)"
                    )
                    endControlFlow()
                    addStatement("return createEnumSet(bits)")
                }
            )
        }

        val factoryMutableEmptyOrFull = KotlinFunctionSpec(mutableInterfaceName, mutableType.ref()) {
            applyVisibility(visibility)
            addParameter(
                KotlinValueParameterSpec("full", KotlinClassNames.BOOLEAN.ref()) {
                    defaultValue("false")
                }
            )
            addDoc("Creates a mutable empty or full set for [$enumRef].")
            addStatement("return $mutableImplName(if (full) FULL_BITS else 0${if (bitsetKind == BitsetKind.I64) "L" else ""})")
        }

        val factoryMutableVararg = KotlinFunctionSpec(mutableInterfaceName, mutableType.ref()) {
            applyVisibility(visibility)
            addParameter(
                KotlinValueParameterSpec("entries", keyTypeRef) {
                    addModifier(KotlinModifier.VARARG)
                }
            )
            addDoc("Creates a mutable set containing [entries].")
            addCode(
                CodeValue {
                    addStatement("var bits = 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                    beginControlFlow("for (entry in entries)")
                    addStatement(
                        "bits = bits or (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl entry.ordinal)"
                    )
                    endControlFlow()
                    addStatement("return $mutableImplName(bits)")
                }
            )
        }

        val factoryMutableIterable = KotlinFunctionSpec(mutableInterfaceName, mutableType.ref()) {
            applyVisibility(visibility)
            addParameter(KotlinValueParameterSpec("entries", iterableTypeRef))
            addDoc("Creates a mutable set containing [entries].")
            addCode(
                CodeValue {
                    addStatement("var bits = 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                    beginControlFlow("for (entry in entries)")
                    addStatement(
                        "bits = bits or (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl entry.ordinal)"
                    )
                    endControlFlow()
                    addStatement("return $mutableImplName(bits)")
                }
            )
        }

        val iteratorType = ClassName("kotlin.collections", "Iterator").parameterized(keyTypeRef).ref()
        val mutableIteratorType = ClassName("kotlin.collections", "MutableIterator").parameterized(keyTypeRef).ref()
        val anyNullableRef = KotlinClassNames.ANY.ref { kotlinStatus { nullable = true } }

        val immutableImpl = KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, implName) {
            addModifier(KotlinModifier.PRIVATE)
            addDoc("Immutable implementation for [$immutableInterfaceName].")
            primaryConstructor(
                KotlinConstructorSpec {
                    addParameter(
                        KotlinValueParameterSpec("bs", bitsetTypeRef) {
                            addModifier(KotlinModifier.OVERRIDE)
                            immutableProperty()
                        }
                    )
                }
            )

            addSuperinterface(immutableType)
            addSuperinterface(bitsetBasedType)

            addFunction(
                KotlinFunctionSpec("contains", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("element", keyTypeRef))
                    val shift = if (bitsetKind == BitsetKind.I64) "1L" else "1"
                    addStatement("return (bs and ($shift shl element.ordinal)) != 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
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
                            addStatement("if (currentBits == 0${if (bitsetKind == BitsetKind.I64) "L" else ""}) return false")
                            beginControlFlow("if (elements is BitsetBased)")
                            addStatement("return (currentBits and elements.bs) == elements.bs")
                            endControlFlow()
                            beginControlFlow("if (elements is Set<*> && elements.size > currentBits.countOneBits())")
                            addStatement("return false")
                            endControlFlow()
                            beginControlFlow("for (element in elements)")
                            addStatement("if (!contains(element)) return false")
                            endControlFlow()
                            addStatement("return true")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("containsAny", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("elements", collectionTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (elements.isEmpty()) return false")
                            addStatement("val currentBits = bs")
                            addStatement("if (currentBits == 0${if (bitsetKind == BitsetKind.I64) "L" else ""}) return false")
                            beginControlFlow("if (elements is BitsetBased)")
                            addStatement("return (currentBits and elements.bs) != 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                            endControlFlow()
                            beginControlFlow("for (element in elements)")
                            addStatement("if ((currentBits and (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl element.ordinal)) != 0${if (bitsetKind == BitsetKind.I64) "L" else ""}) return true")
                            endControlFlow()
                            addStatement("return false")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("intersect", setTypeRef) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val currentBits = bs")
                            addStatement("if (currentBits == 0${if (bitsetKind == BitsetKind.I64) "L" else ""} || other.isEmpty()) return EMPTY")
                            addStatement("if (other === this) return this")
                            beginControlFlow("if (other is BitsetBased)")
                            addStatement("val intersectBits = currentBits and other.bs")
                            addStatement("if (intersectBits == currentBits) return this")
                            addStatement("return createEnumSet(intersectBits)")
                            endControlFlow()
                            addStatement("var intersectBits = 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                            beginControlFlow("if (other.size < currentBits.countOneBits())")
                            beginControlFlow("for (element in other)")
                            addStatement("val mask = 1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl element.ordinal")
                            beginControlFlow("if ((currentBits and mask) != 0${if (bitsetKind == BitsetKind.I64) "L" else ""})")
                            addStatement("intersectBits = intersectBits or mask")
                            endControlFlow()
                            endControlFlow()
                            endControlFlow()
                            beginControlFlow("else")
                            addStatement("var remaining = currentBits")
                            beginControlFlow("while (remaining != 0${if (bitsetKind == BitsetKind.I64) "L" else ""})")
                            addStatement("val bit = remaining.countTrailingZeroBits()")
                            addStatement("if (other.contains($enumRef.entries[bit])) intersectBits = intersectBits or (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl bit)")
                            addStatement("remaining = remaining and (remaining - 1${if (bitsetKind == BitsetKind.I64) "L" else ""})")
                            endControlFlow()
                            endControlFlow()
                            addStatement("if (intersectBits == currentBits) return this")
                            addStatement("return createEnumSet(intersectBits)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("union", setTypeRef) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val currentBits = bs")
                            addStatement("if (other.isEmpty()) return this")
                            addStatement("if (other === this) return this")
                            beginControlFlow("if (other is BitsetBased)")
                            addStatement("val mergedBits = currentBits or other.bs")
                            addStatement("if (mergedBits == currentBits) return this")
                            addStatement("return createEnumSet(mergedBits)")
                            endControlFlow()
                            addStatement("var mergedBits = currentBits")
                            beginControlFlow("for (element in other)")
                            addStatement("mergedBits = mergedBits or (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl element.ordinal)")
                            endControlFlow()
                            addStatement("if (mergedBits == currentBits) return this")
                            addStatement("return createEnumSet(mergedBits)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("difference", setTypeRef) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val currentBits = bs")
                            addStatement("if (currentBits == 0${if (bitsetKind == BitsetKind.I64) "L" else ""} || other.isEmpty()) return this")
                            addStatement("if (other === this) return EMPTY")
                            beginControlFlow("if (other is BitsetBased)")
                            addStatement("val differenceBits = currentBits and other.bs.inv()")
                            addStatement("if (differenceBits == currentBits) return this")
                            addStatement("return createEnumSet(differenceBits)")
                            endControlFlow()
                            addStatement("var removeMask = 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                            beginControlFlow("for (element in other)")
                            addStatement("removeMask = removeMask or (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl element.ordinal)")
                            endControlFlow()
                            addStatement("if (removeMask == 0${if (bitsetKind == BitsetKind.I64) "L" else ""}) return this")
                            addStatement("val differenceBits = currentBits and removeMask.inv()")
                            addStatement("if (differenceBits == currentBits) return this")
                            addStatement("return createEnumSet(differenceBits)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("isEmpty", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return bs == 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                }
            )

            addFunction(
                KotlinFunctionSpec("iterator", iteratorType) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addCode(
                        CodeValue {
                            val zero = if (bitsetKind == BitsetKind.I64) "0L" else "0"
                            beginControlFlow("return object : Iterator<$enumRef>")
                            addStatement("private var remaining = bs")
                            addStatement("override fun hasNext(): Boolean = remaining != $zero")
                            beginControlFlow("override fun next(): $enumRef")
                            addStatement("val current = remaining")
                            addStatement("if (current == $zero) throw NoSuchElementException()")
                            addStatement("val bit = current.countTrailingZeroBits()")
                            addStatement("remaining = current and (current - 1${if (bitsetKind == BitsetKind.I64) "L" else ""})")
                            addStatement("return $enumRef.entries[bit]")
                            endControlFlow()
                            endControlFlow()
                        }
                    )
                }
            )

            addProperty(
                KotlinPropertySpec("size", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    getter { addStatement("return bs.countOneBits()") }
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
                    addParameter(KotlinValueParameterSpec("other", anyNullableRef))
                    addCode(
                        CodeValue {
                            addStatement("if (this === other) return true")
                            addStatement("if (other !is Set<*>) return false")
                            beginControlFlow("if (other is BitsetBased)")
                            addStatement("return bs == other.bs")
                            endControlFlow()
                            addStatement("val currentBits = bs")
                            addStatement("if (other.size != currentBits.countOneBits()) return false")
                            beginControlFlow("for (element in other)")
                            addStatement("if (element !is $enumRef) return false")
                            addStatement("val ordinal = element.ordinal")
                            addStatement("if ((currentBits and (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl ordinal)) == 0${if (bitsetKind == BitsetKind.I64) "L" else ""}) return false")
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
                            beginControlFlow("while (remaining != 0${if (bitsetKind == BitsetKind.I64) "L" else ""})")
                            addStatement("val bit = remaining.countTrailingZeroBits()")
                            addStatement("hash += $enumRef.entries[bit].hashCode()")
                            addStatement("remaining = remaining and (remaining - 1${if (bitsetKind == BitsetKind.I64) "L" else ""})")
                            endControlFlow()
                            addStatement("return hash")
                        }
                    )
                }
            )
        }

        val mutableImpl = KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, mutableImplName) {
            addModifier(KotlinModifier.PRIVATE)
            addDoc("Mutable implementation for [$mutableInterfaceName].")
            primaryConstructor(
                KotlinConstructorSpec {
                    addParameter(
                        KotlinValueParameterSpec("bs", bitsetTypeRef) {
                            addModifier(KotlinModifier.OVERRIDE)
                            mutableProperty()
                        }
                    )
                }
            )

            addSuperinterface(mutableType)
            addSuperinterface(bitsetBasedType)

            addFunction(
                KotlinFunctionSpec("copy", mutableType.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return $mutableImplName(bs)")
                }
            )

            addFunction(
                KotlinFunctionSpec("contains", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("element", keyTypeRef))
                    val shift = if (bitsetKind == BitsetKind.I64) "1L" else "1"
                    addStatement("return (bs and ($shift shl element.ordinal)) != 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
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
                            addStatement("if (currentBits == 0${if (bitsetKind == BitsetKind.I64) "L" else ""}) return false")
                            beginControlFlow("if (elements is BitsetBased)")
                            addStatement("return (currentBits and elements.bs) == elements.bs")
                            endControlFlow()
                            beginControlFlow("if (elements is Set<*> && elements.size > currentBits.countOneBits())")
                            addStatement("return false")
                            endControlFlow()
                            beginControlFlow("for (element in elements)")
                            addStatement("if (!contains(element)) return false")
                            endControlFlow()
                            addStatement("return true")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("containsAny", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("elements", collectionTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (elements.isEmpty()) return false")
                            addStatement("val currentBits = bs")
                            addStatement("if (currentBits == 0${if (bitsetKind == BitsetKind.I64) "L" else ""}) return false")
                            beginControlFlow("if (elements is BitsetBased)")
                            addStatement("return (currentBits and elements.bs) != 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                            endControlFlow()
                            beginControlFlow("for (element in elements)")
                            addStatement("if ((currentBits and (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl element.ordinal)) != 0${if (bitsetKind == BitsetKind.I64) "L" else ""}) return true")
                            endControlFlow()
                            addStatement("return false")
                        }
                    )
                }
            )

            // Immutable operations return immutable snapshots (never `this`).
            addFunction(
                KotlinFunctionSpec("intersect", setTypeRef) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val currentBits = bs")
                            addStatement("if (currentBits == 0${if (bitsetKind == BitsetKind.I64) "L" else ""} || other.isEmpty()) return EMPTY")
                            addStatement("if (other === this) return createEnumSet(currentBits)")
                            beginControlFlow("if (other is BitsetBased)")
                            addStatement("return createEnumSet(currentBits and other.bs)")
                            endControlFlow()
                            addStatement("var intersectBits = 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                            beginControlFlow("if (other.size < currentBits.countOneBits())")
                            beginControlFlow("for (element in other)")
                            addStatement("val mask = 1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl element.ordinal")
                            beginControlFlow("if ((currentBits and mask) != 0${if (bitsetKind == BitsetKind.I64) "L" else ""})")
                            addStatement("intersectBits = intersectBits or mask")
                            endControlFlow()
                            endControlFlow()
                            endControlFlow()
                            beginControlFlow("else")
                            addStatement("var remaining = currentBits")
                            beginControlFlow("while (remaining != 0${if (bitsetKind == BitsetKind.I64) "L" else ""})")
                            addStatement("val bit = remaining.countTrailingZeroBits()")
                            addStatement("if (other.contains($enumRef.entries[bit])) intersectBits = intersectBits or (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl bit)")
                            addStatement("remaining = remaining and (remaining - 1${if (bitsetKind == BitsetKind.I64) "L" else ""})")
                            endControlFlow()
                            endControlFlow()
                            addStatement("return createEnumSet(intersectBits)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("union", setTypeRef) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val currentBits = bs")
                            addStatement("if (other.isEmpty()) return createEnumSet(currentBits)")
                            addStatement("if (other === this) return createEnumSet(currentBits)")
                            beginControlFlow("if (other is BitsetBased)")
                            addStatement("return createEnumSet(currentBits or other.bs)")
                            endControlFlow()
                            addStatement("var mergedBits = currentBits")
                            beginControlFlow("for (element in other)")
                            addStatement("mergedBits = mergedBits or (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl element.ordinal)")
                            endControlFlow()
                            addStatement("return createEnumSet(mergedBits)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("difference", setTypeRef) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val currentBits = bs")
                            addStatement("if (currentBits == 0${if (bitsetKind == BitsetKind.I64) "L" else ""} || other.isEmpty()) return createEnumSet(currentBits)")
                            addStatement("if (other === this) return EMPTY")
                            beginControlFlow("if (other is BitsetBased)")
                            addStatement("return createEnumSet(currentBits and other.bs.inv())")
                            endControlFlow()
                            addStatement("var removeMask = 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                            beginControlFlow("for (element in other)")
                            addStatement("removeMask = removeMask or (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl element.ordinal)")
                            endControlFlow()
                            addStatement("return createEnumSet(currentBits and removeMask.inv())")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("isEmpty", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return bs == 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                }
            )

            addFunction(
                KotlinFunctionSpec("iterator", mutableIteratorType) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addCode(
                        CodeValue {
                            val zero = if (bitsetKind == BitsetKind.I64) "0L" else "0"
                            beginControlFlow("return object : MutableIterator<$enumRef>")
                            addStatement("private var remaining = bs")
                            addStatement("private var lastBit = -1")
                            addStatement("override fun hasNext(): Boolean = remaining != $zero")
                            beginControlFlow("override fun next(): $enumRef")
                            addStatement("val current = remaining")
                            addStatement("if (current == $zero) throw NoSuchElementException()")
                            addStatement("val bit = current.countTrailingZeroBits()")
                            addStatement("lastBit = bit")
                            addStatement("remaining = current and (current - 1${if (bitsetKind == BitsetKind.I64) "L" else ""})")
                            addStatement("return $enumRef.entries[bit]")
                            endControlFlow()
                            beginControlFlow("override fun remove()")
                            addStatement("check(lastBit >= 0) { \"next() must be called before remove()\" }")
                            addStatement("bs = bs and (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl lastBit).inv()")
                            addStatement("lastBit = -1")
                            endControlFlow()
                            endControlFlow()
                        }
                    )
                }
            )

            addProperty(
                KotlinPropertySpec("size", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    getter { addStatement("return bs.countOneBits()") }
                }
            )

            addFunction(
                KotlinFunctionSpec("add", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("element", keyTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val oldBits = bs")
                            addStatement("bs = oldBits or (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl element.ordinal)")
                            addStatement("return bs != oldBits")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("addAll", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("elements", collectionTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (elements.isEmpty()) return false")
                            beginControlFlow("if (elements is BitsetBased)")
                            addStatement("val oldBits = bs")
                            addStatement("bs = oldBits or elements.bs")
                            addStatement("return bs != oldBits")
                            endControlFlow()
                            addStatement("val oldBits = bs")
                            addStatement("var mergedBits = oldBits")
                            beginControlFlow("for (element in elements)")
                            addStatement("mergedBits = mergedBits or (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl element.ordinal)")
                            endControlFlow()
                            addStatement("bs = mergedBits")
                            addStatement("return bs != oldBits")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("clear") {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("bs = 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                }
            )

            addFunction(
                KotlinFunctionSpec("remove", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("element", keyTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val oldBits = bs")
                            addStatement("bs = oldBits and (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl element.ordinal).inv()")
                            addStatement("return bs != oldBits")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("removeAll", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("elements", collectionTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (elements.isEmpty() || bs == 0${if (bitsetKind == BitsetKind.I64) "L" else ""}) return false")
                            beginControlFlow("if (elements is BitsetBased)")
                            addStatement("val oldBits = bs")
                            addStatement("bs = oldBits and elements.bs.inv()")
                            addStatement("return bs != oldBits")
                            endControlFlow()
                            addStatement("var removeMask = 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                            beginControlFlow("for (element in elements)")
                            addStatement("removeMask = removeMask or (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl element.ordinal)")
                            endControlFlow()
                            addStatement("if (removeMask == 0${if (bitsetKind == BitsetKind.I64) "L" else ""}) return false")
                            addStatement("val oldBits = bs")
                            addStatement("bs = oldBits and removeMask.inv()")
                            addStatement("return bs != oldBits")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("retainAll", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("elements", collectionTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val currentBits = bs")
                            addStatement("if (currentBits == 0${if (bitsetKind == BitsetKind.I64) "L" else ""}) return false")
                            beginControlFlow("if (elements is BitsetBased)")
                            addStatement("val retainedBits = currentBits and elements.bs")
                            addStatement("if (retainedBits == currentBits) return false")
                            addStatement("bs = retainedBits")
                            addStatement("return true")
                            endControlFlow()
                            beginControlFlow("if (elements.isEmpty())")
                            addStatement("bs = 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                            addStatement("return true")
                            endControlFlow()
                            addStatement("var retainedBits = 0${if (bitsetKind == BitsetKind.I64) "L" else ""}")
                            beginControlFlow("if (elements is Set<*> && elements.size > currentBits.countOneBits())")
                            addStatement("var remaining = currentBits")
                            beginControlFlow("while (remaining != 0${if (bitsetKind == BitsetKind.I64) "L" else ""})")
                            addStatement("val bit = remaining.countTrailingZeroBits()")
                            addStatement("if (elements.contains($enumRef.entries[bit])) retainedBits = retainedBits or (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl bit)")
                            addStatement("remaining = remaining and (remaining - 1${if (bitsetKind == BitsetKind.I64) "L" else ""})")
                            endControlFlow()
                            endControlFlow()
                            beginControlFlow("else")
                            beginControlFlow("for (element in elements)")
                            addStatement("val mask = 1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl element.ordinal")
                            addStatement("retainedBits = retainedBits or (currentBits and mask)")
                            endControlFlow()
                            endControlFlow()
                            addStatement("if (retainedBits == currentBits) return false")
                            addStatement("bs = retainedBits")
                            addStatement("return true")
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
                    addParameter(KotlinValueParameterSpec("other", anyNullableRef))
                    addCode(
                        CodeValue {
                            addStatement("if (this === other) return true")
                            addStatement("if (other !is Set<*>) return false")
                            beginControlFlow("if (other is BitsetBased)")
                            addStatement("return bs == other.bs")
                            endControlFlow()
                            addStatement("val currentBits = bs")
                            addStatement("if (other.size != currentBits.countOneBits()) return false")
                            beginControlFlow("for (element in other)")
                            addStatement("if (element !is $enumRef) return false")
                            addStatement("val ordinal = element.ordinal")
                            addStatement("if ((currentBits and (1${if (bitsetKind == BitsetKind.I64) "L" else ""} shl ordinal)) == 0${if (bitsetKind == BitsetKind.I64) "L" else ""}) return false")
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
                            beginControlFlow("while (remaining != 0${if (bitsetKind == BitsetKind.I64) "L" else ""})")
                            addStatement("val bit = remaining.countTrailingZeroBits()")
                            addStatement("hash += $enumRef.entries[bit].hashCode()")
                            addStatement("remaining = remaining and (remaining - 1${if (bitsetKind == BitsetKind.I64) "L" else ""})")
                            endControlFlow()
                            addStatement("return hash")
                        }
                    )
                }
            )
        }

        return FileSpec(
            types = listOf(
                immutableInterface,
                mutableInterface,
                privateBitsetInterface,
                immutableImpl,
                mutableImpl,
            ),
            functions = listOf(
                utilToMutable,
                utilToImmutable,
                factoryImmutableEmptyOrFull,
                factoryImmutableVararg,
                factoryImmutableIterable,
                factoryMutableEmptyOrFull,
                factoryMutableVararg,
                factoryMutableIterable,
                createImmutableFunction,
            ),
            properties = listOf(
                fullBitsProperty,
                emptyProperty,
                fullProperty,
            )
        )
    }


}
