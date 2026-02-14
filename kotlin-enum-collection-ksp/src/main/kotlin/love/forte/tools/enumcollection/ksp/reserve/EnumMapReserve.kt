package love.forte.tools.enumcollection.ksp.reserve

import com.google.devtools.ksp.symbol.KSFile
import love.forte.codegentle.common.code.CodeValue
import love.forte.codegentle.common.code.beginControlFlow
import love.forte.codegentle.common.code.endControlFlow
import love.forte.codegentle.common.naming.ClassName
import love.forte.codegentle.common.naming.ParameterizedTypeName
import love.forte.codegentle.common.naming.TypeVariableName
import love.forte.codegentle.common.naming.canonicalName
import love.forte.codegentle.common.naming.parameterized
import love.forte.codegentle.common.naming.simpleNames
import love.forte.codegentle.common.ref.TypeRef
import love.forte.codegentle.common.ref.annotationRef
import love.forte.codegentle.common.ref.ref
import love.forte.codegentle.kotlin.KotlinModifier
import love.forte.codegentle.kotlin.naming.KotlinAnnotationNames
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
 * Reserve for generating a concrete `XxxEnumMap` type.
 *
 * The generated implementation is optimized based on the enum entry count:
 * - `Int` bitset for <= 32 entries
 * - `Long` bitset for <= 64 entries
 * - `LongArray` bitset for larger enums
 *
 * @author Forte Scarlet
 *
 */
internal class EnumMapReserve(
    override val sources: Set<KSFile>,
    override val targetName: String,
    override val visibility: String,
    override val enumDetail: EnumDetail,
) : EnumCollectionReserve() {
    override val apiInheritanceTypeQualifiedName: String = API_ENUM_MAP.canonicalName

    override fun generateType(
        inheritanceAvailable: Boolean,
        inheritanceMode: InheritanceMode
    ): KotlinTypeSpec {
        val inheritApi = shouldInheritApiType(inheritanceMode, inheritanceAvailable)
        val enumType = ClassName(enumDetail.qualifiedName)
        val enumRef = enumType.simpleNames.joinToString(".")
        val entrySize = enumDetail.elements.size

        return when {
            entrySize <= Int.SIZE_BITS -> generateI32EnumMapType(enumType, enumRef, inheritApi)
            entrySize <= Long.SIZE_BITS -> generateI64EnumMapType(enumType, enumRef, inheritApi)
            else -> generateLargeEnumMapType(enumType, enumRef, inheritApi)
        }
    }

    private fun generateI32EnumMapType(enumType: ClassName, enumRef: String, inheritApi: Boolean): KotlinTypeSpec {
        val keyTypeRef = enumType.ref()
        val valueTypeVar = TypeVariableName("V").ref()

        val mapType = KotlinClassNames.MAP.parameterized(keyTypeRef, valueTypeVar)
        val superInterface = if (inheritApi) API_ENUM_MAP.parameterized(keyTypeRef, valueTypeVar) else mapType

        val abstractMapType = ClassName("kotlin.collections", "AbstractMap").parameterized(keyTypeRef, valueTypeVar)

        val entryType = ClassName("kotlin.collections", "Map", "Entry").parameterized(keyTypeRef, valueTypeVar)
        val entriesType = KotlinClassNames.SET.parameterized(entryType.ref())
        val entriesTypeRef = entriesType.ref()

        val anyNullableRef = KotlinClassNames.ANY.ref { kotlinStatus { nullable = true } }
        val slotsTypeRef = KotlinClassNames.ARRAY.parameterized(anyNullableRef).ref()

        val selfClassName = ClassName(enumDetail.packageName.ifBlank { null }, targetName)
        val selfTypeRef = selfClassName.parameterized(valueTypeVar).ref()

        val suppressUncheckedCast = KotlinAnnotationNames.SUPPRESS.annotationRef {
            addMember(format = "\"UNCHECKED_CAST\"")
        }

        return KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, targetName) {
            applyVisibility(visibility)
            addDoc("An immutable enum map optimized for [$enumRef].")
            addTypeVariable(valueTypeVar)

            primaryConstructor(
                KotlinConstructorSpec {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(
                        KotlinValueParameterSpec("keyBits", KotlinClassNames.INT.ref()) {
                            addModifier(KotlinModifier.PRIVATE)
                            immutableProperty()
                        }
                    )
                    addParameter(
                        KotlinValueParameterSpec("slots", slotsTypeRef) {
                            addModifier(KotlinModifier.PRIVATE)
                            immutableProperty()
                        }
                    )
                }
            )

            superclass(abstractMapType)
            addSuperinterface(superInterface)

            addFunction(
                KotlinFunctionSpec("containsOrdinal", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(KotlinValueParameterSpec("ordinal", KotlinClassNames.INT.ref()))
                    addStatement("return (keyBits and (1 shl ordinal)) != 0")
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
                    addStatement("return keyBits == 0")
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
                            beginControlFlow("while (remaining != 0)")
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("if (slots[ordinal] == value) return true")
                            addStatement("remaining = remaining and (remaining - 1)")
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
                            addStatement("override val size: Int get() = this@$targetName.size")
                            addStatement("override fun iterator(): Iterator<Map.Entry<$enumRef, V>> = EntryIterator()")
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

            addSubtype(generateI32EntryIteratorType(enumRef, keyTypeRef, valueTypeVar))
            addSubtype(generateEntryImplType(enumRef, keyTypeRef, valueTypeVar))

            addFunction(
                KotlinFunctionSpec("equals", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("other", anyNullableRef))
                    addCode(
                        CodeValue {
                            addStatement("if (this === other) return true")
                            addStatement("if (other !is Map<*, *>) return false")
                            addStatement("if (size != other.size) return false")

                            beginControlFlow("if (other is $targetName<*>)")
                            addStatement("if (keyBits != other.keyBits) return false")
                            addStatement("var remaining = keyBits")
                            beginControlFlow("while (remaining != 0)")
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("if (slots[ordinal] != other.slots[ordinal]) return false")
                            addStatement("remaining = remaining and (remaining - 1)")
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
                            beginControlFlow("while (remaining != 0)")
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("val keyHash = $enumRef.entries[ordinal].hashCode()")
                            addStatement("val valueHash = slots[ordinal]?.hashCode() ?: 0")
                            addStatement("hash += keyHash xor valueHash")
                            addStatement("remaining = remaining and (remaining - 1)")
                            endControlFlow()
                            addStatement("return hash")
                        }
                    )
                }
            )

            addSubtype(generateI32CompanionObject(enumRef, keyTypeRef, selfTypeRef, slotsTypeRef, valueTypeVar))
        }
    }

    private fun generateI64EnumMapType(enumType: ClassName, enumRef: String, inheritApi: Boolean): KotlinTypeSpec {
        val keyTypeRef = enumType.ref()
        val valueTypeVar = TypeVariableName("V").ref()

        val mapType = KotlinClassNames.MAP.parameterized(keyTypeRef, valueTypeVar)
        val superInterface = if (inheritApi) API_ENUM_MAP.parameterized(keyTypeRef, valueTypeVar) else mapType

        val abstractMapType = ClassName("kotlin.collections", "AbstractMap").parameterized(keyTypeRef, valueTypeVar)

        val entryType = ClassName("kotlin.collections", "Map", "Entry").parameterized(keyTypeRef, valueTypeVar)
        val entriesType = KotlinClassNames.SET.parameterized(entryType.ref())
        val entriesTypeRef = entriesType.ref()

        val anyNullableRef = KotlinClassNames.ANY.ref { kotlinStatus { nullable = true } }
        val slotsTypeRef = KotlinClassNames.ARRAY.parameterized(anyNullableRef).ref()

        val selfClassName = ClassName(enumDetail.packageName.ifBlank { null }, targetName)
        val selfTypeRef = selfClassName.parameterized(valueTypeVar).ref()

        val suppressUncheckedCast = KotlinAnnotationNames.SUPPRESS.annotationRef {
            addMember(format = "\"UNCHECKED_CAST\"")
        }

        return KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, targetName) {
            applyVisibility(visibility)
            addDoc("An immutable enum map optimized for [$enumRef].")
            addTypeVariable(valueTypeVar)

            primaryConstructor(
                KotlinConstructorSpec {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(
                        KotlinValueParameterSpec("keyBits", KotlinClassNames.LONG.ref()) {
                            addModifier(KotlinModifier.PRIVATE)
                            immutableProperty()
                        }
                    )
                    addParameter(
                        KotlinValueParameterSpec("slots", slotsTypeRef) {
                            addModifier(KotlinModifier.PRIVATE)
                            immutableProperty()
                        }
                    )
                }
            )

            superclass(abstractMapType)
            addSuperinterface(superInterface)

            addFunction(
                KotlinFunctionSpec("containsOrdinal", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(KotlinValueParameterSpec("ordinal", KotlinClassNames.INT.ref()))
                    addStatement("return (keyBits and (1L shl ordinal)) != 0L")
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
                    addStatement("return keyBits == 0L")
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
                            beginControlFlow("while (remaining != 0L)")
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("if (slots[ordinal] == value) return true")
                            addStatement("remaining = remaining and (remaining - 1)")
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
                            addStatement("override val size: Int get() = this@$targetName.size")
                            addStatement("override fun iterator(): Iterator<Map.Entry<$enumRef, V>> = EntryIterator()")
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

            addSubtype(generateI64EntryIteratorType(enumRef, keyTypeRef, valueTypeVar))
            addSubtype(generateEntryImplType(enumRef, keyTypeRef, valueTypeVar))

            addFunction(
                KotlinFunctionSpec("equals", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("other", anyNullableRef))
                    addCode(
                        CodeValue {
                            addStatement("if (this === other) return true")
                            addStatement("if (other !is Map<*, *>) return false")
                            addStatement("if (size != other.size) return false")

                            beginControlFlow("if (other is $targetName<*>)")
                            addStatement("if (keyBits != other.keyBits) return false")
                            addStatement("var remaining = keyBits")
                            beginControlFlow("while (remaining != 0L)")
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("if (slots[ordinal] != other.slots[ordinal]) return false")
                            addStatement("remaining = remaining and (remaining - 1)")
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
                            beginControlFlow("while (remaining != 0L)")
                            addStatement("val ordinal = remaining.countTrailingZeroBits()")
                            addStatement("val keyHash = $enumRef.entries[ordinal].hashCode()")
                            addStatement("val valueHash = slots[ordinal]?.hashCode() ?: 0")
                            addStatement("hash += keyHash xor valueHash")
                            addStatement("remaining = remaining and (remaining - 1)")
                            endControlFlow()
                            addStatement("return hash")
                        }
                    )
                }
            )

            addSubtype(generateI64CompanionObject(enumRef, keyTypeRef, selfTypeRef, slotsTypeRef, valueTypeVar))
        }
    }

    private fun generateLargeEnumMapType(enumType: ClassName, enumRef: String, inheritApi: Boolean): KotlinTypeSpec {
        val keyTypeRef = enumType.ref()
        val valueTypeVar = TypeVariableName("V").ref()

        val mapType = KotlinClassNames.MAP.parameterized(keyTypeRef, valueTypeVar)
        val superInterface = if (inheritApi) API_ENUM_MAP.parameterized(keyTypeRef, valueTypeVar) else mapType

        val abstractMapType = ClassName("kotlin.collections", "AbstractMap").parameterized(keyTypeRef, valueTypeVar)

        val entryType = ClassName("kotlin.collections", "Map", "Entry").parameterized(keyTypeRef, valueTypeVar)
        val entriesType = KotlinClassNames.SET.parameterized(entryType.ref())
        val entriesTypeRef = entriesType.ref()

        val anyNullableRef = KotlinClassNames.ANY.ref { kotlinStatus { nullable = true } }
        val slotsTypeRef = KotlinClassNames.ARRAY.parameterized(anyNullableRef).ref()
        val wordsTypeRef = ClassName("kotlin", "LongArray").ref()

        val selfClassName = ClassName(enumDetail.packageName.ifBlank { null }, targetName)
        val selfTypeRef = selfClassName.parameterized(valueTypeVar).ref()

        val suppressUncheckedCast = KotlinAnnotationNames.SUPPRESS.annotationRef {
            addMember(format = "\"UNCHECKED_CAST\"")
        }

        return KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, targetName) {
            applyVisibility(visibility)
            addDoc("An immutable enum map optimized for [$enumRef].")
            addTypeVariable(valueTypeVar)

            primaryConstructor(
                KotlinConstructorSpec {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(
                        KotlinValueParameterSpec("keyWords", wordsTypeRef) {
                            addModifier(KotlinModifier.PRIVATE)
                            immutableProperty()
                        }
                    )
                    addParameter(
                        KotlinValueParameterSpec("slots", slotsTypeRef) {
                            addModifier(KotlinModifier.PRIVATE)
                            immutableProperty()
                        }
                    )
                }
            )

            superclass(abstractMapType)
            addSuperinterface(superInterface)

            addProperty(
                KotlinPropertySpec("mapSize", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    initializer("bitCountOfWords(keyWords)")
                }
            )

            addFunction(
                KotlinFunctionSpec("containsOrdinal", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(KotlinValueParameterSpec("ordinal", KotlinClassNames.INT.ref()))
                    addCode(
                        CodeValue {
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("if (wordIndex >= keyWords.size) return false")
                            addStatement("return (keyWords[wordIndex] and (1L shl (ordinal and 63))) != 0L")
                        }
                    )
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
                    getter { addCode("return mapSize") }
                }
            )

            addFunction(
                KotlinFunctionSpec("isEmpty", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return mapSize == 0")
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
                            beginControlFlow("for (wordIndex in keyWords.indices)")
                            addStatement("var word = keyWords[wordIndex]")
                            beginControlFlow("while (word != 0L)")
                            addStatement("val bit = word.countTrailingZeroBits()")
                            addStatement("val ordinal = (wordIndex shl 6) + bit")
                            addStatement("if (ordinal < slots.size && slots[ordinal] == value) return true")
                            addStatement("word = word and (word - 1)")
                            endControlFlow()
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
                            addStatement("if (!containsOrdinal(ordinal) || ordinal >= slots.size) return null")
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
                            addStatement("override val size: Int get() = this@$targetName.size")
                            addStatement("override fun iterator(): Iterator<Map.Entry<$enumRef, V>> = EntryIterator()")
                            beginControlFlow("override fun contains(element: Map.Entry<$enumRef, V>): Boolean")
                            addStatement("val ordinal = element.key.ordinal")
                            addStatement("if (!containsOrdinal(ordinal) || ordinal >= slots.size) return false")
                            addStatement("return slots[ordinal] == element.value")
                            endControlFlow()
                            endControlFlow()
                        }
                    )
                }
            )

            addSubtype(generateLargeEntryIteratorType(enumRef, keyTypeRef, valueTypeVar))
            addSubtype(generateEntryImplType(enumRef, keyTypeRef, valueTypeVar))

            addFunction(
                KotlinFunctionSpec("equals", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("other", anyNullableRef))
                    addCode(
                        CodeValue {
                            addStatement("if (this === other) return true")
                            addStatement("if (other !is Map<*, *>) return false")
                            addStatement("if (size != other.size) return false")

                            beginControlFlow("if (other is $targetName<*>)")
                            addStatement("val leftWords = keyWords")
                            addStatement("val rightWords = other.keyWords")
                            addStatement("val minSize = minOf(leftWords.size, rightWords.size)")
                            beginControlFlow("for (wordIndex in 0 until minSize)")
                            addStatement("if (leftWords[wordIndex] != rightWords[wordIndex]) return false")
                            endControlFlow()
                            beginControlFlow("for (wordIndex in minSize until leftWords.size)")
                            addStatement("if (leftWords[wordIndex] != 0L) return false")
                            endControlFlow()
                            beginControlFlow("for (wordIndex in minSize until rightWords.size)")
                            addStatement("if (rightWords[wordIndex] != 0L) return false")
                            endControlFlow()

                            beginControlFlow("for (wordIndex in leftWords.indices)")
                            addStatement("var word = leftWords[wordIndex]")
                            beginControlFlow("while (word != 0L)")
                            addStatement("val bit = word.countTrailingZeroBits()")
                            addStatement("val ordinal = (wordIndex shl 6) + bit")
                            addStatement("val leftValue = if (ordinal < slots.size) slots[ordinal] else null")
                            addStatement("val rightValue = if (ordinal < other.slots.size) other.slots[ordinal] else null")
                            addStatement("if (leftValue != rightValue) return false")
                            addStatement("word = word and (word - 1)")
                            endControlFlow()
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
                            beginControlFlow("for (wordIndex in keyWords.indices)")
                            addStatement("var word = keyWords[wordIndex]")
                            beginControlFlow("while (word != 0L)")
                            addStatement("val bit = word.countTrailingZeroBits()")
                            addStatement("val ordinal = (wordIndex shl 6) + bit")
                            addStatement("val keyHash = $enumRef.entries[ordinal].hashCode()")
                            addStatement("val valueHash = if (ordinal < slots.size) (slots[ordinal]?.hashCode() ?: 0) else 0")
                            addStatement("hash += keyHash xor valueHash")
                            addStatement("word = word and (word - 1)")
                            endControlFlow()
                            endControlFlow()
                            addStatement("return hash")
                        }
                    )
                }
            )

            addSubtype(
                generateLargeCompanionObject(
                    enumRef,
                    keyTypeRef,
                    selfTypeRef,
                    slotsTypeRef,
                    wordsTypeRef,
                    valueTypeVar,
                    enumDetail.elements.size
                )
            )
        }
    }

    private fun generateEntryImplType(
        enumRef: String,
        keyTypeRef: TypeRef<*>,
        valueTypeVar: TypeRef<*>
    ): KotlinTypeSpec {
        val entryType = ClassName("kotlin.collections", "Map", "Entry").parameterized(keyTypeRef, valueTypeVar)
        val anyNullableRef = KotlinClassNames.ANY.ref { kotlinStatus { nullable = true } }

        return KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, "EntryImpl") {
            addModifier(KotlinModifier.PRIVATE)
            addModifier(KotlinModifier.INNER)

            primaryConstructor(
                KotlinConstructorSpec {
                    addParameter(
                        KotlinValueParameterSpec("ordinal", KotlinClassNames.INT.ref()) {
                            addModifier(KotlinModifier.PRIVATE)
                            immutableProperty()
                        }
                    )
                }
            )

            addSuperinterface(entryType)

            addProperty(
                KotlinPropertySpec("key", keyTypeRef) {
                    addModifier(KotlinModifier.OVERRIDE)
                    getter { addStatement("return $enumRef.entries[ordinal]") }
                }
            )

            addProperty(
                KotlinPropertySpec("value", valueTypeVar) {
                    addModifier(KotlinModifier.OVERRIDE)
                    getter { addStatement("return valueAt(ordinal)") }
                }
            )

            addFunction(
                KotlinFunctionSpec("equals", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("other", anyNullableRef))
                    addCode(
                        CodeValue {
                            addStatement("if (other !is Map.Entry<*, *>) return false")
                            addStatement("return key == other.key && value == other.value")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("hashCode", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return key.hashCode() xor (value?.hashCode() ?: 0)")
                }
            )
        }
    }

    private fun generateI32EntryIteratorType(
        enumRef: String,
        keyTypeRef: TypeRef<*>,
        valueTypeVar: TypeRef<*>
    ): KotlinTypeSpec {
        val entryType = ClassName("kotlin.collections", "Map", "Entry").parameterized(keyTypeRef, valueTypeVar)
        val iteratorType = ClassName("kotlin.collections", "Iterator").parameterized(entryType.ref())

        return KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, "EntryIterator") {
            addModifier(KotlinModifier.PRIVATE)
            addModifier(KotlinModifier.INNER)

            addSuperinterface(iteratorType)

            addProperty(
                KotlinPropertySpec("remaining", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    mutable(true)
                    initializer("keyBits")
                }
            )

            addFunction(
                KotlinFunctionSpec("hasNext", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return remaining != 0")
                }
            )

            addFunction(
                KotlinFunctionSpec("next", entryType.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addCode(
                        CodeValue {
                            addStatement("val current = remaining")
                            addStatement("if (current == 0) throw NoSuchElementException()")
                            addStatement("val ordinal = current.countTrailingZeroBits()")
                            addStatement("remaining = current and (current - 1)")
                            addStatement("return EntryImpl(ordinal)")
                        }
                    )
                }
            )
        }
    }

    private fun generateI64EntryIteratorType(
        enumRef: String,
        keyTypeRef: TypeRef<*>,
        valueTypeVar: TypeRef<*>
    ): KotlinTypeSpec {
        val entryType = ClassName("kotlin.collections", "Map", "Entry").parameterized(keyTypeRef, valueTypeVar)
        val iteratorType = ClassName("kotlin.collections", "Iterator").parameterized(entryType.ref())

        return KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, "EntryIterator") {
            addModifier(KotlinModifier.PRIVATE)
            addModifier(KotlinModifier.INNER)

            addSuperinterface(iteratorType)

            addProperty(
                KotlinPropertySpec("remaining", KotlinClassNames.LONG.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    mutable(true)
                    initializer("keyBits")
                }
            )

            addFunction(
                KotlinFunctionSpec("hasNext", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return remaining != 0L")
                }
            )

            addFunction(
                KotlinFunctionSpec("next", entryType.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addCode(
                        CodeValue {
                            addStatement("val current = remaining")
                            addStatement("if (current == 0L) throw NoSuchElementException()")
                            addStatement("val ordinal = current.countTrailingZeroBits()")
                            addStatement("remaining = current and (current - 1)")
                            addStatement("return EntryImpl(ordinal)")
                        }
                    )
                }
            )
        }
    }

    private fun generateLargeEntryIteratorType(
        enumRef: String,
        keyTypeRef: TypeRef<*>,
        valueTypeVar: TypeRef<*>
    ): KotlinTypeSpec {
        val entryType = ClassName("kotlin.collections", "Map", "Entry").parameterized(keyTypeRef, valueTypeVar)
        val iteratorType = ClassName("kotlin.collections", "Iterator").parameterized(entryType.ref())

        return KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, "EntryIterator") {
            addModifier(KotlinModifier.PRIVATE)
            addModifier(KotlinModifier.INNER)

            addSuperinterface(iteratorType)

            addProperty(
                KotlinPropertySpec("currentWordIndex", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    mutable(true)
                    initializer("0")
                }
            )

            addProperty(
                KotlinPropertySpec("currentWord", KotlinClassNames.LONG.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    mutable(true)
                    initializer("if (keyWords.isNotEmpty()) keyWords[0] else 0L")
                }
            )

            addFunction(
                KotlinFunctionSpec("hasNext", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addCode(
                        CodeValue {
                            beginControlFlow("while (currentWord == 0L && currentWordIndex < keyWords.lastIndex)")
                            addStatement("currentWordIndex++")
                            addStatement("currentWord = keyWords[currentWordIndex]")
                            endControlFlow()
                            addStatement("return currentWord != 0L")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("next", entryType.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addCode(
                        CodeValue {
                            addStatement("if (!hasNext()) throw NoSuchElementException()")
                            addStatement("val bit = currentWord.countTrailingZeroBits()")
                            addStatement("val ordinal = (currentWordIndex shl 6) + bit")
                            addStatement("currentWord = currentWord and (currentWord - 1)")
                            addStatement("return EntryImpl(ordinal)")
                        }
                    )
                }
            )
        }
    }

    private fun generateI32CompanionObject(
        enumRef: String,
        keyTypeRef: TypeRef<*>,
        selfTypeRef: TypeRef<*>,
        slotsTypeRef: TypeRef<*>,
        valueTypeVar: TypeRef<*>
    ): KotlinObjectTypeSpec {
        val pairTypeRef = ClassName("kotlin", "Pair").parameterized(keyTypeRef, valueTypeVar).ref()
        val suppressUncheckedCast = KotlinAnnotationNames.SUPPRESS.annotationRef {
            addMember(format = "\"UNCHECKED_CAST\"")
        }
        // Companion objects cannot be generic, so we keep a single EMPTY instance and cast it.
        val anyNullableRef = KotlinClassNames.ANY.ref { kotlinStatus { nullable = true } }
        val emptySelfTypeRef =
            (selfTypeRef.typeName as ParameterizedTypeName).rawType.parameterized(anyNullableRef).ref()

        return KotlinObjectTypeSpec {
            addProperty(
                KotlinPropertySpec("EMPTY", emptySelfTypeRef) {
                    addModifier(KotlinModifier.PRIVATE)
                    initializer("$targetName(0, emptyArray<Any?>())")
                }
            )

            addFunction(
                KotlinFunctionSpec("empty", selfTypeRef) {
                    addTypeVariable(valueTypeVar as TypeRef<TypeVariableName>)
                    addAnnotation(suppressUncheckedCast)
                    addStatement("return EMPTY as $targetName<V>")
                }
            )

            addFunction(
                KotlinFunctionSpec("highestOrdinalPlusOne", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(KotlinValueParameterSpec("bits", KotlinClassNames.INT.ref()))
                    addCode(
                        CodeValue {
                            addStatement("if (bits == 0) return 0")
                            addStatement("return Int.SIZE_BITS - bits.countLeadingZeroBits()")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("create", selfTypeRef) {
                    addModifier(KotlinModifier.PRIVATE)
                    addTypeVariable(valueTypeVar as TypeRef<TypeVariableName>)
                    addParameter(KotlinValueParameterSpec("bits", KotlinClassNames.INT.ref()))
                    addParameter(KotlinValueParameterSpec("slots", slotsTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (bits == 0) return empty()")
                            addStatement("val requiredSlotSize = highestOrdinalPlusOne(bits)")
                            addStatement("val normalizedSlots = if (slots.size == requiredSlotSize) slots else slots.copyOf(requiredSlotSize)")
                            addStatement("return $targetName(bits, normalizedSlots)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("of", selfTypeRef) {
                    addTypeVariable(valueTypeVar as TypeRef<TypeVariableName>)
                    addParameter(
                        KotlinValueParameterSpec("pairs", pairTypeRef) {
                            addModifier(KotlinModifier.VARARG)
                        }
                    )
                    addCode(
                        CodeValue {
                            addStatement("if (pairs.isEmpty()) return empty()")
                            addStatement("var bits = 0")
                            addStatement("var maxOrdinal = -1")
                            beginControlFlow("for ((key) in pairs)")
                            addStatement("val ordinal = key.ordinal")
                            addStatement("bits = bits or (1 shl ordinal)")
                            beginControlFlow("if (ordinal > maxOrdinal)")
                            addStatement("maxOrdinal = ordinal")
                            endControlFlow()
                            endControlFlow()
                            addStatement("val slots = arrayOfNulls<Any?>(maxOrdinal + 1)")
                            beginControlFlow("for ((key, value) in pairs)")
                            addStatement("slots[key.ordinal] = value")
                            endControlFlow()
                            addStatement("return create(bits, slots)")
                        }
                    )
                }
            )
        }
    }

    private fun generateI64CompanionObject(
        enumRef: String,
        keyTypeRef: TypeRef<*>,
        selfTypeRef: TypeRef<*>,
        slotsTypeRef: TypeRef<*>,
        valueTypeVar: TypeRef<*>
    ): KotlinObjectTypeSpec {
        val pairTypeRef = ClassName("kotlin", "Pair").parameterized(keyTypeRef, valueTypeVar).ref()
        val suppressUncheckedCast = KotlinAnnotationNames.SUPPRESS.annotationRef {
            addMember(format = "\"UNCHECKED_CAST\"")
        }
        // Companion objects cannot be generic, so we keep a single EMPTY instance and cast it.
        val anyNullableRef = KotlinClassNames.ANY.ref { kotlinStatus { nullable = true } }
        val emptySelfTypeRef =
            (selfTypeRef.typeName as ParameterizedTypeName).rawType.parameterized(anyNullableRef).ref()

        return KotlinObjectTypeSpec {
            addProperty(
                KotlinPropertySpec("EMPTY", emptySelfTypeRef) {
                    addModifier(KotlinModifier.PRIVATE)
                    initializer("$targetName(0L, emptyArray<Any?>())")
                }
            )

            addFunction(
                KotlinFunctionSpec("empty", selfTypeRef) {
                    addTypeVariable(valueTypeVar as TypeRef<TypeVariableName>)
                    addAnnotation(suppressUncheckedCast)
                    addStatement("return EMPTY as $targetName<V>")
                }
            )

            addFunction(
                KotlinFunctionSpec("highestOrdinalPlusOne", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(KotlinValueParameterSpec("bits", KotlinClassNames.LONG.ref()))
                    addCode(
                        CodeValue {
                            addStatement("if (bits == 0L) return 0")
                            addStatement("return Long.SIZE_BITS - bits.countLeadingZeroBits()")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("create", selfTypeRef) {
                    addModifier(KotlinModifier.PRIVATE)
                    addTypeVariable(valueTypeVar as TypeRef<TypeVariableName>)
                    addParameter(KotlinValueParameterSpec("bits", KotlinClassNames.LONG.ref()))
                    addParameter(KotlinValueParameterSpec("slots", slotsTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (bits == 0L) return empty()")
                            addStatement("val requiredSlotSize = highestOrdinalPlusOne(bits)")
                            addStatement("val normalizedSlots = if (slots.size == requiredSlotSize) slots else slots.copyOf(requiredSlotSize)")
                            addStatement("return $targetName(bits, normalizedSlots)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("of", selfTypeRef) {
                    addTypeVariable(valueTypeVar as TypeRef<TypeVariableName>)
                    addParameter(
                        KotlinValueParameterSpec("pairs", pairTypeRef) {
                            addModifier(KotlinModifier.VARARG)
                        }
                    )
                    addCode(
                        CodeValue {
                            addStatement("if (pairs.isEmpty()) return empty()")
                            addStatement("var bits = 0L")
                            addStatement("var maxOrdinal = -1")
                            beginControlFlow("for ((key) in pairs)")
                            addStatement("val ordinal = key.ordinal")
                            addStatement("bits = bits or (1L shl ordinal)")
                            beginControlFlow("if (ordinal > maxOrdinal)")
                            addStatement("maxOrdinal = ordinal")
                            endControlFlow()
                            endControlFlow()
                            addStatement("val slots = arrayOfNulls<Any?>(maxOrdinal + 1)")
                            beginControlFlow("for ((key, value) in pairs)")
                            addStatement("slots[key.ordinal] = value")
                            endControlFlow()
                            addStatement("return create(bits, slots)")
                        }
                    )
                }
            )
        }
    }

    private fun generateLargeCompanionObject(
        enumRef: String,
        keyTypeRef: TypeRef<*>,
        selfTypeRef: TypeRef<*>,
        slotsTypeRef: TypeRef<*>,
        wordsTypeRef: TypeRef<*>,
        valueTypeVar: TypeRef<*>,
        entrySize: Int,
    ): KotlinObjectTypeSpec {
        val pairTypeRef = ClassName("kotlin", "Pair").parameterized(keyTypeRef, valueTypeVar).ref()
        val suppressUncheckedCast = KotlinAnnotationNames.SUPPRESS.annotationRef {
            addMember(format = "\"UNCHECKED_CAST\"")
        }
        // Companion objects cannot be generic, so we keep a single EMPTY instance and cast it.
        val anyNullableRef = KotlinClassNames.ANY.ref { kotlinStatus { nullable = true } }
        val emptySelfTypeRef =
            (selfTypeRef.typeName as ParameterizedTypeName).rawType.parameterized(anyNullableRef).ref()

        return KotlinObjectTypeSpec {
            addProperty(
                KotlinPropertySpec("EMPTY", emptySelfTypeRef) {
                    addModifier(KotlinModifier.PRIVATE)
                    initializer("$targetName(LongArray(0), emptyArray<Any?>())")
                }
            )

            addFunction(
                KotlinFunctionSpec("empty", selfTypeRef) {
                    addTypeVariable(valueTypeVar as TypeRef<TypeVariableName>)
                    addAnnotation(suppressUncheckedCast)
                    addStatement("return EMPTY as $targetName<V>")
                }
            )

            // Helpers are placed in companion to keep the generated type lean.
            addFunction(
                KotlinFunctionSpec("lastNonZeroWordIndex", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(KotlinValueParameterSpec("words", wordsTypeRef))
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
                KotlinFunctionSpec("highestOrdinalPlusOne", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(KotlinValueParameterSpec("words", wordsTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("if (words.isEmpty()) return 0")
                            addStatement("val lastWordIndex = words.lastIndex")
                            addStatement("val lastWord = words[lastWordIndex]")
                            addStatement("if (lastWord == 0L) return 0")
                            addStatement("val highestBit = 63 - lastWord.countLeadingZeroBits()")
                            addStatement("return (lastWordIndex shl 6) + highestBit + 1")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("bitCountOfWords", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    addParameter(KotlinValueParameterSpec("words", wordsTypeRef))
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
                    addTypeVariable(valueTypeVar as TypeRef<TypeVariableName>)
                    addParameter(KotlinValueParameterSpec("words", wordsTypeRef))
                    addParameter(KotlinValueParameterSpec("slots", slotsTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val lastWordIndex = lastNonZeroWordIndex(words)")
                            addStatement("if (lastWordIndex < 0) return empty()")
                            addStatement(
                                "val trimmedWords = if (lastWordIndex == words.lastIndex) words else words.copyOf(lastWordIndex + 1)"
                            )
                            addStatement("val requiredSlotSize = highestOrdinalPlusOne(trimmedWords)")
                            addStatement("val normalizedSlots = if (slots.size == requiredSlotSize) slots else slots.copyOf(requiredSlotSize)")
                            addStatement("return $targetName(trimmedWords, normalizedSlots)")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("of", selfTypeRef) {
                    addTypeVariable(valueTypeVar as TypeRef<TypeVariableName>)
                    addParameter(
                        KotlinValueParameterSpec("pairs", pairTypeRef) {
                            addModifier(KotlinModifier.VARARG)
                        }
                    )
                    addCode(
                        CodeValue {
                            addStatement("if (pairs.isEmpty()) return empty()")
                            addStatement("var maxOrdinal = -1")
                            beginControlFlow("for ((key) in pairs)")
                            addStatement("val ordinal = key.ordinal")
                            beginControlFlow("if (ordinal > maxOrdinal)")
                            addStatement("maxOrdinal = ordinal")
                            endControlFlow()
                            endControlFlow()

                            addStatement("val words = LongArray((maxOrdinal + 64) ushr 6)")
                            addStatement("val slots = arrayOfNulls<Any?>(maxOrdinal + 1)")
                            beginControlFlow("for ((key, value) in pairs)")
                            addStatement("val ordinal = key.ordinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("words[wordIndex] = words[wordIndex] or (1L shl (ordinal and 63))")
                            addStatement("slots[ordinal] = value")
                            endControlFlow()
                            addStatement("return create(words, slots)")
                        }
                    )
                }
            )
        }
    }
}
