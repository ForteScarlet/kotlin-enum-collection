package love.forte.tools.enumcollection.ksp.reserve

import love.forte.codegentle.common.code.CodeValue
import love.forte.codegentle.common.code.beginControlFlow
import love.forte.codegentle.common.code.endControlFlow
import love.forte.codegentle.common.naming.ClassName
import love.forte.codegentle.common.naming.LowerWildcardTypeName
import love.forte.codegentle.common.naming.TypeVariableName
import love.forte.codegentle.common.naming.parameterized
import love.forte.codegentle.common.ref.annotationRef
import love.forte.codegentle.common.ref.ref
import love.forte.codegentle.kotlin.KotlinModifier
import love.forte.codegentle.kotlin.naming.KotlinAnnotationNames
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
 * Generates bitset-based enum map implementations.
 *
 * Storage is either `Int` (<= 32 entries) or `Long` (<= 64 entries).
 */
internal object EnumMapBitsetFileGenerator {

    internal enum class BitsetKind {
        I32,
        I64,
    }

    internal fun generateFileSpec(
        enumDetail: EnumDetail,
        targetName: String,
        visibility: String,
        generatedPackageName: String,
        enumType: ClassName,
        enumRef: String,
        enumSize: Int,
        inheritApi: Boolean,
        bitsetKind: BitsetKind,
    ): FileSpec {
        val packageName = generatedPackageName.ifBlank { null }
        val keyTypeRef = enumType.ref()
        val valueTypeVar = TypeVariableName("V").ref()

        val immutableInterfaceName = targetName
        val mutableInterfaceName = "Mutable$targetName"
        val implName = "${targetName}Impl"
        val mutableImplName = "Mutable${targetName}Impl"

        val immutableType = ClassName(packageName, immutableInterfaceName)
        val mutableType = ClassName(packageName, mutableInterfaceName)
        val immutableTypeRef = immutableType.parameterized(valueTypeVar).ref()
        val mutableTypeRef = mutableType.parameterized(valueTypeVar).ref()

        val mapType = KotlinClassNames.MAP.parameterized(keyTypeRef, valueTypeVar)
        val mutableMapType = KotlinClassNames.MUTABLE_MAP.parameterized(keyTypeRef, valueTypeVar)

        val superImmutable = if (inheritApi) API_ENUM_MAP.parameterized(keyTypeRef, valueTypeVar) else mapType
        val superMutable = if (inheritApi) API_MUTABLE_ENUM_MAP.parameterized(keyTypeRef, valueTypeVar) else mutableMapType

        val bitsTypeRef = when (bitsetKind) {
            BitsetKind.I32 -> KotlinClassNames.INT.ref()
            BitsetKind.I64 -> KotlinClassNames.LONG.ref()
        }

        val zeroLiteral = if (bitsetKind == BitsetKind.I64) "0L" else "0"
        val oneLiteral = if (bitsetKind == BitsetKind.I64) "1L" else "1"
        val whileNonZero = if (bitsetKind == BitsetKind.I64) "while (remaining != 0L)" else "while (remaining != 0)"
        val bitAndNonZero = if (bitsetKind == BitsetKind.I64) "!= 0L" else "!= 0"
        val minusOneLiteral = if (bitsetKind == BitsetKind.I64) "1L" else "1"
        val bitsSizeLiteral = if (bitsetKind == BitsetKind.I64) "Long.SIZE_BITS" else "Int.SIZE_BITS"

        val anyNullableRef = KotlinClassNames.ANY.ref { kotlinStatus { nullable = true } }
        val slotsTypeRef = KotlinClassNames.ARRAY.parameterized(anyNullableRef).ref()

        val entryType = ClassName("kotlin.collections", "Map", "Entry").parameterized(keyTypeRef, valueTypeVar)
        val entriesType = KotlinClassNames.SET.parameterized(entryType.ref())
        val entriesTypeRef = entriesType.ref()

        val abstractMapType = ClassName("kotlin.collections", "AbstractMap").parameterized(keyTypeRef, valueTypeVar)
        val abstractMutableMapType =
            ClassName("kotlin.collections", "AbstractMutableMap").parameterized(keyTypeRef, valueTypeVar)

        val entryTypeRef = entryType.ref()
        val iteratorTypeRef = ClassName("kotlin.collections", "Iterator").parameterized(entryTypeRef).ref()

        val suppressUncheckedCast = KotlinAnnotationNames.SUPPRESS.annotationRef {
            addMember(format = "\"UNCHECKED_CAST\"")
        }


        val immutableInterface = KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.INTERFACE, immutableInterfaceName) {
            applyVisibility(visibility)
            addDoc("An enum-specialized map optimized for [$enumRef] keys.")
            addTypeVariable(valueTypeVar)
            addSuperinterface(superImmutable)
        }

        val mutableInterface = KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.INTERFACE, mutableInterfaceName) {
            applyVisibility(visibility)
            addDoc("A mutable variant of [$immutableInterfaceName].")
            addTypeVariable(valueTypeVar)
            addSuperinterface(immutableType.parameterized(valueTypeVar))
            addSuperinterface(superMutable)
            addFunction(
                KotlinFunctionSpec("copy", mutableTypeRef) {
                    overrideIf(inheritApi)
                }
            )
        }

        val emptyTypeRef = immutableType.parameterized(anyNullableRef).ref()
        val emptyProperty = KotlinPropertySpec("EMPTY", emptyTypeRef) {
            addModifier(KotlinModifier.PRIVATE)
            initializer("$implName($zeroLiteral, emptyArray<Any?>())")
        }

        val emptyFunction = KotlinFunctionSpec("emptyEnumMap", immutableTypeRef) {
            addModifier(KotlinModifier.PRIVATE)
            addTypeVariable(valueTypeVar)
            addAnnotation(suppressUncheckedCast)
            addStatement("return EMPTY as $immutableInterfaceName<V>")
        }

        val highestOrdinalPlusOne = KotlinFunctionSpec("highestOrdinalPlusOne", KotlinClassNames.INT.ref()) {
            addModifier(KotlinModifier.PRIVATE)
            addParameter(KotlinValueParameterSpec("bits", bitsTypeRef))
            addCode(
                CodeValue {
                    addStatement("if (bits == $zeroLiteral) return 0")
                    addStatement("return $bitsSizeLiteral - bits.countLeadingZeroBits()")
                }
            )
        }

        val createImmutableFunction = KotlinFunctionSpec("createEnumMap", immutableTypeRef) {
            addModifier(KotlinModifier.PRIVATE)
            addTypeVariable(valueTypeVar)
            addParameter(KotlinValueParameterSpec("bits", bitsTypeRef))
            addParameter(KotlinValueParameterSpec("slots", slotsTypeRef))
            addCode(
                CodeValue {
                    addStatement("if (bits == $zeroLiteral) return emptyEnumMap()")
                    addStatement("val requiredSlotSize = highestOrdinalPlusOne(bits)")
                    addStatement(
                        "val normalizedSlots = if (slots.size == requiredSlotSize) slots else slots.copyOf(requiredSlotSize)"
                    )
                    addStatement("return $implName(bits, normalizedSlots)")
                }
            )
        }

        val createImmutableCopyFunction = KotlinFunctionSpec("createEnumMapCopy", immutableTypeRef) {
            addModifier(KotlinModifier.PRIVATE)
            addTypeVariable(valueTypeVar)
            addParameter(KotlinValueParameterSpec("bits", bitsTypeRef))
            addParameter(KotlinValueParameterSpec("slots", slotsTypeRef))
            addCode(
                CodeValue {
                    addStatement("if (bits == $zeroLiteral) return emptyEnumMap()")
                    addStatement("val requiredSlotSize = highestOrdinalPlusOne(bits)")
                    addStatement("return $implName(bits, slots.copyOf(requiredSlotSize))")
                }
            )
        }

        val utilToMutable = KotlinFunctionSpec("mutable", mutableTypeRef) {
            applyVisibility(visibility)
            addTypeVariable(valueTypeVar)
            receiver(immutableTypeRef)
            addDoc("Returns an independent mutable copy of this map.")
            addCode(
                CodeValue {
                    beginControlFlow("return when (this)")
                    addStatement("is $mutableInterfaceName<*> -> (this as $mutableInterfaceName<V>).copy()")
                    addStatement("is $implName<*> -> $mutableImplName(keyBits, slots.copyOf($enumSize))")
                    addStatement("else -> $mutableInterfaceName(this)")
                    endControlFlow()
                }
            )
        }

        val utilToImmutable = KotlinFunctionSpec("immutable", immutableTypeRef) {
            applyVisibility(visibility)
            addTypeVariable(valueTypeVar)
            receiver(mutableTypeRef)
            addDoc("Returns an immutable snapshot of this mutable map.")
            addCode(
                CodeValue {
                    beginControlFlow("return when (this)")
                    addStatement("is $mutableImplName<*> -> createEnumMapCopy(keyBits, slots)")
                    addStatement("else -> $immutableInterfaceName(this)")
                    endControlFlow()
                }
            )
        }

        val factoryImmutableEmpty = KotlinFunctionSpec(immutableInterfaceName, immutableTypeRef) {
            applyVisibility(visibility)
            addTypeVariable(valueTypeVar)
            addDoc("Creates an immutable empty map for [$enumRef].")
            addStatement("return emptyEnumMap()")
        }

        val pairTypeRef = ClassName("kotlin", "Pair").parameterized(keyTypeRef, valueTypeVar).ref()
        val factoryImmutableVararg = KotlinFunctionSpec(immutableInterfaceName, immutableTypeRef) {
            applyVisibility(visibility)
            addTypeVariable(valueTypeVar)
            addParameter(
                KotlinValueParameterSpec("pairs", pairTypeRef) {
                    addModifier(KotlinModifier.VARARG)
                }
            )
            addDoc("Creates an immutable map containing [pairs].")
            addCode(
                CodeValue {
                    addStatement("if (pairs.isEmpty()) return emptyEnumMap()")
                    addStatement("var bits = $zeroLiteral")
                    addStatement("var maxOrdinal = -1")
                    beginControlFlow("for ((key) in pairs)")
                    addStatement("val ordinal = key.ordinal")
                    addStatement("bits = bits or ($oneLiteral shl ordinal)")
                    beginControlFlow("if (ordinal > maxOrdinal)")
                    addStatement("maxOrdinal = ordinal")
                    endControlFlow()
                    endControlFlow()
                    addStatement("val slots = arrayOfNulls<Any?>(maxOrdinal + 1)")
                    beginControlFlow("for ((key, value) in pairs)")
                    addStatement("slots[key.ordinal] = value")
                    endControlFlow()
                    addStatement("return createEnumMap(bits, slots)")
                }
            )
        }

        val mapTypeRef = mapType.ref()
        val mapOutKeyTypeRef = KotlinClassNames.MAP
            .parameterized(LowerWildcardTypeName(keyTypeRef).ref(), valueTypeVar)
            .ref()
        val factoryImmutableMap = KotlinFunctionSpec(immutableInterfaceName, immutableTypeRef) {
            applyVisibility(visibility)
            addTypeVariable(valueTypeVar)
            addAnnotation(suppressUncheckedCast)
            addParameter(KotlinValueParameterSpec("from", mapTypeRef))
            addDoc("Creates an immutable map containing entries from [from].")
            addCode(
                CodeValue {
                    beginControlFlow(
                        "if (from is $immutableInterfaceName<*> && from !is $mutableInterfaceName<*>)"
                    )
                    addStatement("return from as $immutableInterfaceName<V>")
                    endControlFlow()

                    beginControlFlow("if (from is $implName<*>)")
                    addStatement("return createEnumMapCopy(from.keyBits, from.slots)")
                    endControlFlow()

                    beginControlFlow("if (from is $mutableImplName<*>)")
                    addStatement("return createEnumMapCopy(from.keyBits, from.slots)")
                    endControlFlow()

                    addStatement("if (from.isEmpty()) return emptyEnumMap()")
                    addStatement("val slots = arrayOfNulls<Any?>($enumSize)")
                    addStatement("var bits = $zeroLiteral")
                    beginControlFlow("for ((key, value) in from)")
                    addStatement("val ordinal = key.ordinal")
                    addStatement("bits = bits or ($oneLiteral shl ordinal)")
                    addStatement("slots[ordinal] = value")
                    endControlFlow()
                    addStatement("return createEnumMap(bits, slots)")
                }
            )
        }

        val factoryMutableEmpty = KotlinFunctionSpec(mutableInterfaceName, mutableTypeRef) {
            applyVisibility(visibility)
            addTypeVariable(valueTypeVar)
            addDoc("Creates an empty mutable map for [$enumRef].")
            addStatement("return $mutableImplName($zeroLiteral, arrayOfNulls($enumSize))")
        }

        val factoryMutableVararg = KotlinFunctionSpec(mutableInterfaceName, mutableTypeRef) {
            applyVisibility(visibility)
            addTypeVariable(valueTypeVar)
            addParameter(
                KotlinValueParameterSpec("pairs", pairTypeRef) {
                    addModifier(KotlinModifier.VARARG)
                }
            )
            addDoc("Creates a mutable map containing [pairs].")
            addCode(
                CodeValue {
                    addStatement("val slots = arrayOfNulls<Any?>($enumSize)")
                    addStatement("var bits = $zeroLiteral")
                    beginControlFlow("for ((key, value) in pairs)")
                    addStatement("val ordinal = key.ordinal")
                    addStatement("bits = bits or ($oneLiteral shl ordinal)")
                    addStatement("slots[ordinal] = value")
                    endControlFlow()
                    addStatement("return $mutableImplName(bits, slots)")
                }
            )
        }

        val factoryMutableMap = KotlinFunctionSpec(mutableInterfaceName, mutableTypeRef) {
            applyVisibility(visibility)
            addTypeVariable(valueTypeVar)
            addParameter(KotlinValueParameterSpec("from", mapTypeRef))
            addDoc("Creates a mutable map containing entries from [from].")
            addCode(
                CodeValue {
                    beginControlFlow("if (from is $mutableInterfaceName<*>)")
                    addStatement("return (from as $mutableInterfaceName<V>).copy()")
                    endControlFlow()

                    beginControlFlow("if (from is $implName<*>)")
                    addStatement("return $mutableImplName(from.keyBits, from.slots.copyOf($enumSize))")
                    endControlFlow()

                    addStatement("val slots = arrayOfNulls<Any?>($enumSize)")
                    addStatement("var bits = $zeroLiteral")
                    beginControlFlow("for ((key, value) in from)")
                    addStatement("val ordinal = key.ordinal")
                    addStatement("bits = bits or ($oneLiteral shl ordinal)")
                    addStatement("slots[ordinal] = value")
                    endControlFlow()
                    addStatement("return $mutableImplName(bits, slots)")
                }
            )
        }

        val immutableImpl = KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, implName) {
            addModifier(KotlinModifier.PRIVATE)
            addDoc("Immutable implementation for [$immutableInterfaceName].")
            addTypeVariable(valueTypeVar)

            primaryConstructor(
                KotlinConstructorSpec {
                    addParameter(
                        KotlinValueParameterSpec("keyBits", bitsTypeRef) {
                            immutableProperty()
                        }
                    )
                    addParameter(
                        KotlinValueParameterSpec("slots", slotsTypeRef) {
                            immutableProperty()
                        }
                    )
                }
            )

            superclass(abstractMapType)
            addSuperinterface(immutableType.parameterized(valueTypeVar))

            addFunction(
                KotlinFunctionSpec("containsOrdinal", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(KotlinValueParameterSpec("ordinal", KotlinClassNames.INT.ref()))
                    addStatement("return (keyBits and ($oneLiteral shl ordinal)) $bitAndNonZero")
                }
            )

            addFunction(
                KotlinFunctionSpec("valueAt", valueTypeVar) {
                    addModifier(KotlinModifier.PRIVATE)
                    addAnnotation(suppressUncheckedCast)
                    addParameter(KotlinValueParameterSpec("ordinal", KotlinClassNames.INT.ref()))
                    addStatement("return slots[ordinal] as V")
                }
            )

            addProperty(
                KotlinPropertySpec("size", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    getter { addCode("return keyBits.countOneBits()") }
                }
            )

            addFunction(
                KotlinFunctionSpec("isEmpty", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return keyBits == $zeroLiteral")
                }
            )

            addFunction(
                KotlinFunctionSpec("containsKey", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("key", keyTypeRef))
                    addStatement("return containsOrdinal(key.ordinal)")
                }
            )

            addFunction(
                KotlinFunctionSpec("containsValue", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("value", valueTypeVar))
                    addCode(
                        CodeValue {
                            addStatement("var remaining = keyBits")
                            beginControlFlow(whileNonZero)
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("if (slots[ordinal] == value) return true")
                            addStatement("remaining = remaining and (remaining - $minusOneLiteral)")
                            endControlFlow()
                            addStatement("return false")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("get", valueTypeVar.typeName.ref { kotlinStatus { nullable = true } }) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("key", keyTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val ordinal = key.ordinal")
                            addStatement("if (!containsOrdinal(ordinal)) return null")
                            addStatement("return valueAt(ordinal)")
                        }
                    )
                }
            )

            val cachedEntriesTypeRef = entriesType.ref { kotlinStatus { nullable = true } }
            addProperty(
                KotlinPropertySpec("cachedEntries", cachedEntriesTypeRef) {
                    addModifier(KotlinModifier.PRIVATE)
                    mutable(true)
                    initializer("null")
                }
            )

            addProperty(
                KotlinPropertySpec("entries", entriesTypeRef) {
                    addModifier(KotlinModifier.OVERRIDE)
                    getter {
                        addCode(
                            CodeValue {
                                addStatement("val cached = cachedEntries")
                                addStatement("if (cached != null) return cached")
                                addStatement("val created = createEntries()")
                                addStatement("cachedEntries = created")
                                addStatement("return created")
                            }
                        )
                    }
                }
            )

            addFunction(
                KotlinFunctionSpec("createEntries", entriesTypeRef) {
                    addModifier(KotlinModifier.PRIVATE)
                    addCode(
                        CodeValue {
                            beginControlFlow("return object : AbstractSet<Map.Entry<$enumRef, V>>()")
                            addStatement("override val size: Int get() = this@$implName.size")
                            addStatement(
                                "override fun iterator(): Iterator<Map.Entry<$enumRef, V>> = createIterator()"
                            )
                            beginControlFlow("override fun contains(element: Map.Entry<$enumRef, V>): Boolean")
                            addStatement("val ordinal = element.key.ordinal")
                            addStatement("if (!containsOrdinal(ordinal)) return false")
                            addStatement("return slots[ordinal] == element.value")
                            endControlFlow()
                            endControlFlow()
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("createIterator", iteratorTypeRef) {
                    addModifier(KotlinModifier.PRIVATE)
                    addCode(
                        CodeValue {
                            beginControlFlow("return object : Iterator<Map.Entry<$enumRef, V>>")
                            addStatement("private var remaining = keyBits")
                            addStatement("override fun hasNext(): Boolean = remaining != $zeroLiteral")
                            beginControlFlow("override fun next(): Map.Entry<$enumRef, V>")
                            addStatement("val current = remaining")
                            addStatement("if (current == $zeroLiteral) throw NoSuchElementException()")
                            addStatement("val ordinal = current.countTrailingZeroBits()")
                            addStatement("remaining = current and (current - $minusOneLiteral)")
                            beginControlFlow("return object : Map.Entry<$enumRef, V>")
                            addStatement("override val key: $enumRef get() = $enumRef.entries[ordinal]")
                            addStatement("override val value: V get() = valueAt(ordinal)")
                            beginControlFlow("override fun equals(other: Any?): Boolean")
                            addStatement("if (other !is Map.Entry<*, *>) return false")
                            addStatement("return key == other.key && value == other.value")
                            endControlFlow()
                            addStatement(
                                "override fun hashCode(): Int = key.hashCode() xor (slots[ordinal]?.hashCode() ?: 0)"
                            )
                            endControlFlow()
                            endControlFlow()
                            endControlFlow()
                        }
                    )
                }
            )

            val anyNullableRefInEquals = anyNullableRef
            addFunction(
                KotlinFunctionSpec("equals", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("other", anyNullableRefInEquals))
                    addCode(
                        CodeValue {
                            addStatement("if (this === other) return true")
                            addStatement("if (other !is Map<*, *>) return false")
                            addStatement("if (size != other.size) return false")

                            beginControlFlow("if (other is $implName<*>)")
                            addStatement("if (keyBits != other.keyBits) return false")
                            addStatement("var remaining = keyBits")
                            beginControlFlow(whileNonZero)
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("if (slots[ordinal] != other.slots[ordinal]) return false")
                            addStatement("remaining = remaining and (remaining - $minusOneLiteral)")
                            endControlFlow()
                            addStatement("return true")
                            endControlFlow()

                            beginControlFlow("if (other is $mutableImplName<*>)")
                            addStatement("if (keyBits != other.keyBits) return false")
                            addStatement("var remaining = keyBits")
                            beginControlFlow(whileNonZero)
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("if (slots[ordinal] != other.slots[ordinal]) return false")
                            addStatement("remaining = remaining and (remaining - $minusOneLiteral)")
                            endControlFlow()
                            addStatement("return true")
                            endControlFlow()

                            beginControlFlow("for ((key, value) in entries)")
                            addStatement("val otherValue = other[key]")
                            addStatement(
                                "if (otherValue != value || (value == null && !other.containsKey(key))) return false"
                            )
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
                            addStatement("var remaining = keyBits")
                            beginControlFlow(whileNonZero)
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("val keyHash = $enumRef.entries[ordinal].hashCode()")
                            addStatement("val valueHash = slots[ordinal]?.hashCode() ?: 0")
                            addStatement("hash += keyHash xor valueHash")
                            addStatement("remaining = remaining and (remaining - $minusOneLiteral)")
                            endControlFlow()
                            addStatement("return hash")
                        }
                    )
                }
            )
        }

        val mutableEntryTypeRef = ClassName(null, "MutableMap", "MutableEntry")
            .parameterized(keyTypeRef, valueTypeVar)
            .ref()
        val mutableEntriesType = KotlinClassNames.MUTABLE_SET.parameterized(mutableEntryTypeRef)
        val mutableEntriesTypeRef = mutableEntriesType.ref()

        val mutableIteratorTypeRef =
            ClassName("kotlin.collections", "MutableIterator").parameterized(mutableEntryTypeRef).ref()

        val mutableImpl = KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, mutableImplName) {
            addModifier(KotlinModifier.PRIVATE)
            addDoc("Mutable implementation for [$mutableInterfaceName].")
            addTypeVariable(valueTypeVar)

            primaryConstructor(
                KotlinConstructorSpec {
                    addParameter(
                        KotlinValueParameterSpec("keyBits", bitsTypeRef) {
                            mutableProperty()
                        }
                    )
                    addParameter(
                        KotlinValueParameterSpec("slots", slotsTypeRef) {
                            immutableProperty()
                        }
                    )
                }
            )

            superclass(abstractMutableMapType)
            addSuperinterface(mutableType.parameterized(valueTypeVar))

            addFunction(
                KotlinFunctionSpec("containsOrdinal", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(KotlinValueParameterSpec("ordinal", KotlinClassNames.INT.ref()))
                    addStatement("return (keyBits and ($oneLiteral shl ordinal)) $bitAndNonZero")
                }
            )

            addFunction(
                KotlinFunctionSpec("valueAt", valueTypeVar) {
                    addModifier(KotlinModifier.PRIVATE)
                    addAnnotation(suppressUncheckedCast)
                    addParameter(KotlinValueParameterSpec("ordinal", KotlinClassNames.INT.ref()))
                    addStatement("return slots[ordinal] as V")
                }
            )

            addFunction(
                KotlinFunctionSpec("copy", mutableTypeRef) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return $mutableImplName(keyBits, slots.copyOf())")
                }
            )

            addProperty(
                KotlinPropertySpec("size", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    getter { addCode("return keyBits.countOneBits()") }
                }
            )

            addFunction(
                KotlinFunctionSpec("isEmpty", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return keyBits == $zeroLiteral")
                }
            )

            addFunction(
                KotlinFunctionSpec("containsKey", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("key", keyTypeRef))
                    addStatement("return containsOrdinal(key.ordinal)")
                }
            )

            addFunction(
                KotlinFunctionSpec("containsValue", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("value", valueTypeVar))
                    addCode(
                        CodeValue {
                            addStatement("var remaining = keyBits")
                            beginControlFlow(whileNonZero)
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("if (slots[ordinal] == value) return true")
                            addStatement("remaining = remaining and (remaining - $minusOneLiteral)")
                            endControlFlow()
                            addStatement("return false")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("get", valueTypeVar.typeName.ref { kotlinStatus { nullable = true } }) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("key", keyTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val ordinal = key.ordinal")
                            addStatement("if (!containsOrdinal(ordinal)) return null")
                            addStatement("return valueAt(ordinal)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("put", valueTypeVar.typeName.ref { kotlinStatus { nullable = true } }) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("key", keyTypeRef))
                    addParameter(KotlinValueParameterSpec("value", valueTypeVar))
                    addCode(
                        CodeValue {
                            addStatement("val ordinal = key.ordinal")
                            addStatement("val mask = $oneLiteral shl ordinal")
                            addStatement("val existed = (keyBits and mask) $bitAndNonZero")
                            addStatement("val oldValue = if (existed) valueAt(ordinal) else null")
                            addStatement("slots[ordinal] = value")
                            addStatement("keyBits = keyBits or mask")
                            addStatement("return oldValue")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("remove", valueTypeVar.typeName.ref { kotlinStatus { nullable = true } }) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("key", keyTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val ordinal = key.ordinal")
                            addStatement("val mask = $oneLiteral shl ordinal")
                            addStatement("if ((keyBits and mask) ${if (bitsetKind == BitsetKind.I64) "== 0L" else "== 0"}) return null")
                            addStatement("val oldValue = valueAt(ordinal)")
                            addStatement("keyBits = keyBits and mask.inv()")
                            addStatement("slots[ordinal] = null")
                            addStatement("return oldValue")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("putAll") {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("from", mapOutKeyTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (from.isEmpty()) return")
                            beginControlFlow("if (from is $implName<*>)")
                            addStatement("val sourceBits = from.keyBits")
                            addStatement("if (sourceBits == $zeroLiteral) return")
                            addStatement("keyBits = keyBits or sourceBits")
                            addStatement("var remaining = sourceBits")
                            beginControlFlow(whileNonZero)
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("slots[ordinal] = from.slots[ordinal]")
                            addStatement("remaining = remaining and (remaining - $minusOneLiteral)")
                            endControlFlow()
                            addStatement("return")
                            endControlFlow()

                            beginControlFlow("if (from is $mutableImplName<*>)")
                            addStatement("val sourceBits = from.keyBits")
                            addStatement("if (sourceBits == $zeroLiteral) return")
                            addStatement("keyBits = keyBits or sourceBits")
                            addStatement("var remaining = sourceBits")
                            beginControlFlow(whileNonZero)
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("slots[ordinal] = from.slots[ordinal]")
                            addStatement("remaining = remaining and (remaining - $minusOneLiteral)")
                            endControlFlow()
                            addStatement("return")
                            endControlFlow()

                            beginControlFlow("for ((key, value) in from)")
                            addStatement("put(key, value)")
                            endControlFlow()
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("clear") {
                    addModifier(KotlinModifier.OVERRIDE)
                    addCode(
                        CodeValue {
                            addStatement("var remaining = keyBits")
                            beginControlFlow(whileNonZero)
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("slots[ordinal] = null")
                            addStatement("remaining = remaining and (remaining - $minusOneLiteral)")
                            endControlFlow()
                            addStatement("keyBits = $zeroLiteral")
                        }
                    )
                }
            )

            val cachedMutableEntriesTypeRef = mutableEntriesType.ref { kotlinStatus { nullable = true } }
            addProperty(
                KotlinPropertySpec("cachedEntries", cachedMutableEntriesTypeRef) {
                    addModifier(KotlinModifier.PRIVATE)
                    mutable(true)
                    initializer("null")
                }
            )

            addProperty(
                KotlinPropertySpec("entries", mutableEntriesTypeRef) {
                    addModifier(KotlinModifier.OVERRIDE)
                    getter {
                        addCode(
                            CodeValue {
                                addStatement("val cached = cachedEntries")
                                addStatement("if (cached != null) return cached")
                                addStatement("val created = createEntries()")
                                addStatement("cachedEntries = created")
                                addStatement("return created")
                            }
                        )
                    }
                }
            )

            addFunction(
                KotlinFunctionSpec("createEntries", mutableEntriesTypeRef) {
                    addModifier(KotlinModifier.PRIVATE)
                    addCode(
                        CodeValue {
                            beginControlFlow(
                                "return object : AbstractMutableSet<MutableMap.MutableEntry<$enumRef, V>>()"
                            )
                            addStatement("override val size: Int get() = this@$mutableImplName.size")
                            addStatement(
                                "override fun iterator(): MutableIterator<MutableMap.MutableEntry<$enumRef, V>> = createIterator()"
                            )

                            beginControlFlow(
                                "override fun contains(element: MutableMap.MutableEntry<$enumRef, V>): Boolean"
                            )
                            addStatement("val ordinal = element.key.ordinal")
                            addStatement("if (!containsOrdinal(ordinal)) return false")
                            addStatement("return slots[ordinal] == element.value")
                            endControlFlow()

                            beginControlFlow(
                                "override fun add(element: MutableMap.MutableEntry<$enumRef, V>): Boolean"
                            )
                            addStatement("val ordinal = element.key.ordinal")
                            addStatement("val mask = $oneLiteral shl ordinal")
                            addStatement("val existed = (keyBits and mask) $bitAndNonZero")
                            addStatement("val oldValue = if (existed) slots[ordinal] else null")
                            addStatement("slots[ordinal] = element.value")
                            addStatement("keyBits = keyBits or mask")
                            addStatement("return !existed || oldValue != element.value")
                            endControlFlow()

                            beginControlFlow(
                                "override fun remove(element: MutableMap.MutableEntry<$enumRef, V>): Boolean"
                            )
                            addStatement("if (!contains(element)) return false")
                            addStatement("this@$mutableImplName.remove(element.key)")
                            addStatement("return true")
                            endControlFlow()

                            beginControlFlow("override fun clear()")
                            addStatement("this@$mutableImplName.clear()")
                            endControlFlow()
                            endControlFlow()
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("createIterator", mutableIteratorTypeRef) {
                    addModifier(KotlinModifier.PRIVATE)
                    addCode(
                        CodeValue {
                            beginControlFlow("return object : MutableIterator<MutableMap.MutableEntry<$enumRef, V>>")
                            addStatement("private var remaining = keyBits")
                            addStatement("private var lastOrdinal = -1")
                            addStatement("override fun hasNext(): Boolean = remaining != $zeroLiteral")
                            beginControlFlow(
                                "override fun next(): MutableMap.MutableEntry<$enumRef, V>"
                            )
                            addStatement("val current = remaining")
                            addStatement("if (current == $zeroLiteral) throw NoSuchElementException()")
                            addStatement("val ordinal = current.countTrailingZeroBits()")
                            addStatement("lastOrdinal = ordinal")
                            addStatement("remaining = current and (current - $minusOneLiteral)")
                            addStatement("return createEntry(ordinal)")
                            endControlFlow()
                            beginControlFlow("override fun remove()")
                            addStatement("check(lastOrdinal >= 0) { \"next() must be called before remove()\" }")
                            addStatement("val ordinal = lastOrdinal")
                            addStatement("keyBits = keyBits and ($oneLiteral shl ordinal).inv()")
                            addStatement("slots[ordinal] = null")
                            addStatement("lastOrdinal = -1")
                            endControlFlow()
                            endControlFlow()
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("createEntry", mutableEntryTypeRef) {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(KotlinValueParameterSpec("ordinal", KotlinClassNames.INT.ref()))
                    addCode(
                        CodeValue {
                            beginControlFlow(
                                "return object : MutableMap.MutableEntry<$enumRef, V>"
                            )
                            addStatement("override val key: $enumRef get() = $enumRef.entries[ordinal]")
                            addStatement("override val value: V get() = valueAt(ordinal)")
                            beginControlFlow("override fun setValue(newValue: V): V")
                            addStatement("check(containsOrdinal(ordinal)) { \"Entry is no longer valid\" }")
                            addStatement("val old = valueAt(ordinal)")
                            addStatement("slots[ordinal] = newValue")
                            addStatement("return old")
                            endControlFlow()
                            beginControlFlow("override fun equals(other: Any?): Boolean")
                            addStatement("if (other !is Map.Entry<*, *>) return false")
                            addStatement("return key == other.key && value == other.value")
                            endControlFlow()
                            addStatement(
                                "override fun hashCode(): Int = key.hashCode() xor (slots[ordinal]?.hashCode() ?: 0)"
                            )
                            endControlFlow()
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("equals", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("other", anyNullableRef))
                    addCode(
                        CodeValue {
                            addStatement("if (this === other) return true")
                            addStatement("if (other !is Map<*, *>) return false")
                            addStatement("if (size != other.size) return false")

                            beginControlFlow("if (other is $implName<*>)")
                            addStatement("if (keyBits != other.keyBits) return false")
                            addStatement("var remaining = keyBits")
                            beginControlFlow(whileNonZero)
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("if (slots[ordinal] != other.slots[ordinal]) return false")
                            addStatement("remaining = remaining and (remaining - $minusOneLiteral)")
                            endControlFlow()
                            addStatement("return true")
                            endControlFlow()

                            beginControlFlow("if (other is $mutableImplName<*>)")
                            addStatement("if (keyBits != other.keyBits) return false")
                            addStatement("var remaining = keyBits")
                            beginControlFlow(whileNonZero)
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("if (slots[ordinal] != other.slots[ordinal]) return false")
                            addStatement("remaining = remaining and (remaining - $minusOneLiteral)")
                            endControlFlow()
                            addStatement("return true")
                            endControlFlow()

                            beginControlFlow("for ((key, value) in entries)")
                            addStatement("val otherValue = other[key]")
                            addStatement(
                                "if (otherValue != value || (value == null && !other.containsKey(key))) return false"
                            )
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
                            addStatement("var remaining = keyBits")
                            beginControlFlow(whileNonZero)
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("val keyHash = $enumRef.entries[ordinal].hashCode()")
                            addStatement("val valueHash = slots[ordinal]?.hashCode() ?: 0")
                            addStatement("hash += keyHash xor valueHash")
                            addStatement("remaining = remaining and (remaining - $minusOneLiteral)")
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
                immutableImpl,
                mutableImpl,
            ),
            functions = listOf(
                utilToMutable,
                utilToImmutable,
                factoryImmutableEmpty,
                factoryImmutableVararg,
                factoryImmutableMap,
                factoryMutableEmpty,
                factoryMutableVararg,
                factoryMutableMap,
                emptyFunction,
                highestOrdinalPlusOne,
                createImmutableFunction,
                createImmutableCopyFunction,
            ),
            properties = listOf(
                emptyProperty,
            )
        )
    }


}
