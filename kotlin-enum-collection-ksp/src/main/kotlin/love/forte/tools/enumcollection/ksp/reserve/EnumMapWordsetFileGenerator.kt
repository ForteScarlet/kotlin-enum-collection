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
 * Generates wordset-based enum map implementations.
 *
 * Storage is based on `LongArray` and is used when enum entries > 64.
 */
internal object EnumMapWordsetFileGenerator {

    internal fun generateFileSpec(
        enumDetail: EnumDetail,
        targetName: String,
        visibility: String,
        generatedPackageName: String,
        enumType: ClassName,
        enumRef: String,
        enumSize: Int,
        inheritApi: Boolean,
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

        val anyNullableRef = KotlinClassNames.ANY.ref { kotlinStatus { nullable = true } }
        val slotsTypeRef = KotlinClassNames.ARRAY.parameterized(anyNullableRef).ref()
        val wordsTypeRef = ClassName("kotlin", "LongArray").ref()
        val wordCapacity = (enumSize + 63) ushr 6

        val entryType = ClassName("kotlin.collections", "Map", "Entry").parameterized(keyTypeRef, valueTypeVar)
        val entriesType = KotlinClassNames.SET.parameterized(entryType.ref())
        val entriesTypeRef = entriesType.ref()

        val abstractMapType = ClassName("kotlin.collections", "AbstractMap").parameterized(keyTypeRef, valueTypeVar)
        val abstractMutableMapType =
            ClassName("kotlin.collections", "AbstractMutableMap").parameterized(keyTypeRef, valueTypeVar)

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
                    applyMemberVisibility(visibility)
                }
            )
        }

        val emptyTypeRef = immutableType.parameterized(anyNullableRef).ref()
        val emptyProperty = KotlinPropertySpec("EMPTY", emptyTypeRef) {
            addModifier(KotlinModifier.PRIVATE)
            initializer("$implName(longArrayOf(), emptyArray<Any?>())")
        }

        val emptyFunction = KotlinFunctionSpec("emptyEnumMap", immutableTypeRef) {
            addModifier(KotlinModifier.PRIVATE)
            addTypeVariable(valueTypeVar)
            addAnnotation(suppressUncheckedCast)
            addStatement("return EMPTY as $immutableInterfaceName<V>")
        }

        val lastNonZeroWordIndexFunction = KotlinFunctionSpec("lastNonZeroWordIndex", KotlinClassNames.INT.ref()) {
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

        val trimWordsFunction = KotlinFunctionSpec("trimWords", wordsTypeRef) {
            addModifier(KotlinModifier.PRIVATE)
            addParameter(KotlinValueParameterSpec("words", wordsTypeRef))
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

        val highestOrdinalPlusOneFunction = KotlinFunctionSpec("highestOrdinalPlusOne", KotlinClassNames.INT.ref()) {
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

        val bitCountOfWordsFunction = KotlinFunctionSpec("bitCountOfWords", KotlinClassNames.INT.ref()) {
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

        val createImmutableFunction = KotlinFunctionSpec("createEnumMap", immutableTypeRef) {
            addModifier(KotlinModifier.PRIVATE)
            addTypeVariable(valueTypeVar)
            addParameter(KotlinValueParameterSpec("words", wordsTypeRef))
            addParameter(KotlinValueParameterSpec("slots", slotsTypeRef))
            addCode(
                CodeValue {
                    addStatement("val lastWordIndex = lastNonZeroWordIndex(words)")
                    addStatement("if (lastWordIndex < 0) return emptyEnumMap()")
                    addStatement("val trimmedWords = trimWords(words, lastWordIndex)")
                    addStatement("val requiredSlotSize = highestOrdinalPlusOne(trimmedWords)")
                    addStatement(
                        "val normalizedSlots = if (slots.size == requiredSlotSize) slots else slots.copyOf(requiredSlotSize)"
                    )
                    addStatement("return $implName(trimmedWords, normalizedSlots)")
                }
            )
        }

        val createImmutableCopyFunction = KotlinFunctionSpec("createEnumMapCopy", immutableTypeRef) {
            addModifier(KotlinModifier.PRIVATE)
            addTypeVariable(valueTypeVar)
            addParameter(KotlinValueParameterSpec("words", wordsTypeRef))
            addParameter(KotlinValueParameterSpec("slots", slotsTypeRef))
            addCode(
                CodeValue {
                    addStatement("val lastWordIndex = lastNonZeroWordIndex(words)")
                    addStatement("if (lastWordIndex < 0) return emptyEnumMap()")
                    addStatement("val trimmedWords = trimWords(words.copyOf(), lastWordIndex)")
                    addStatement("val requiredSlotSize = highestOrdinalPlusOne(trimmedWords)")
                    addStatement("return $implName(trimmedWords, slots.copyOf(requiredSlotSize))")
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
                    addStatement(
                        "is $implName<*> -> $mutableImplName(keyWords.copyOf($wordCapacity), slots.copyOf($enumSize), size)"
                    )
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
                    addStatement("is $mutableImplName<*> -> createEnumMapCopy(keyWords, slots)")
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
                    addStatement("return createEnumMap(words, slots)")
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
                    addStatement("return createEnumMapCopy(from.keyWords, from.slots)")
                    endControlFlow()

                    beginControlFlow("if (from is $mutableImplName<*>)")
                    addStatement("return createEnumMapCopy(from.keyWords, from.slots)")
                    endControlFlow()

                    addStatement("if (from.isEmpty()) return emptyEnumMap()")
                    addStatement("var words = LongArray(0)")
                    addStatement("var slots = arrayOfNulls<Any?>(0)")
                    beginControlFlow("for ((key, value) in from)")
                    addStatement("val ordinal = key.ordinal")
                    addStatement("val wordIndex = ordinal ushr 6")
                    beginControlFlow("if (wordIndex >= words.size)")
                    addStatement("words = words.copyOf(wordIndex + 1)")
                    endControlFlow()
                    beginControlFlow("if (ordinal >= slots.size)")
                    addStatement("slots = slots.copyOf(ordinal + 1)")
                    endControlFlow()
                    addStatement("words[wordIndex] = words[wordIndex] or (1L shl (ordinal and 63))")
                    addStatement("slots[ordinal] = value")
                    endControlFlow()
                    addStatement("return createEnumMap(words, slots)")
                }
            )
        }

        val factoryMutableEmpty = KotlinFunctionSpec(mutableInterfaceName, mutableTypeRef) {
            applyVisibility(visibility)
            addTypeVariable(valueTypeVar)
            addDoc("Creates an empty mutable map for [$enumRef].")
            addStatement("return $mutableImplName(LongArray($wordCapacity), arrayOfNulls($enumSize), 0)")
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
                    addStatement("val words = LongArray($wordCapacity)")
                    addStatement("val slots = arrayOfNulls<Any?>($enumSize)")
                    addStatement("var mapSize = 0")
                    beginControlFlow("for ((key, value) in pairs)")
                    addStatement("val ordinal = key.ordinal")
                    addStatement("val wordIndex = ordinal ushr 6")
                    addStatement("val mask = 1L shl (ordinal and 63)")
                    addStatement("val oldWord = words[wordIndex]")
                    beginControlFlow("if ((oldWord and mask) == 0L)")
                    addStatement("words[wordIndex] = oldWord or mask")
                    addStatement("mapSize++")
                    endControlFlow()
                    addStatement("slots[ordinal] = value")
                    endControlFlow()
                    addStatement("return $mutableImplName(words, slots, mapSize)")
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
                        addStatement(
                            "return $mutableImplName(from.keyWords.copyOf($wordCapacity), from.slots.copyOf($enumSize), bitCountOfWords(from.keyWords))"
                        )
                        endControlFlow()

                        beginControlFlow("if (from is $mutableImplName<*>)")
                        addStatement(
                            "return $mutableImplName(from.keyWords.copyOf($wordCapacity), from.slots.copyOf($enumSize), bitCountOfWords(from.keyWords))"
                        )
                        endControlFlow()

                    addStatement("val words = LongArray($wordCapacity)")
                    addStatement("val slots = arrayOfNulls<Any?>($enumSize)")
                    addStatement("var mapSize = 0")
                    beginControlFlow("for ((key, value) in from)")
                    addStatement("val ordinal = key.ordinal")
                    addStatement("val wordIndex = ordinal ushr 6")
                    addStatement("val mask = 1L shl (ordinal and 63)")
                    addStatement("val oldWord = words[wordIndex]")
                    beginControlFlow("if ((oldWord and mask) == 0L)")
                    addStatement("words[wordIndex] = oldWord or mask")
                    addStatement("mapSize++")
                    endControlFlow()
                    addStatement("slots[ordinal] = value")
                    endControlFlow()
                    addStatement("return $mutableImplName(words, slots, mapSize)")
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
                        KotlinValueParameterSpec("keyWords", wordsTypeRef) {
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

            addProperty(
                KotlinPropertySpec("mapSize", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.PRIVATE)
                    initializer("bitCountOfWords(keyWords)")
                }
            )

            superclass(abstractMapType)
            addSuperinterface(immutableType.parameterized(valueTypeVar))

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
                    getter { addStatement("return mapSize") }
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
                            addStatement("override val size: Int get() = this@$implName.size")
                            addStatement(
                                "override fun iterator(): Iterator<Map.Entry<$enumRef, V>> = createIterator()"
                            )
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

            addFunction(
                KotlinFunctionSpec(
                    "createIterator",
                    ClassName("kotlin.collections", "Iterator")
                        .parameterized(entryType.ref())
                        .ref()
                ) {
                    addModifier(KotlinModifier.PRIVATE)
                    addCode(
                        CodeValue {
                            beginControlFlow("return object : Iterator<Map.Entry<$enumRef, V>>")
                            addStatement("private var currentWordIndex = 0")
                            addStatement("private var currentWord = if (keyWords.isNotEmpty()) keyWords[0] else 0L")
                            beginControlFlow("override fun hasNext(): Boolean")
                            beginControlFlow("while (currentWord == 0L && currentWordIndex < keyWords.lastIndex)")
                            addStatement("currentWordIndex++")
                            addStatement("currentWord = keyWords[currentWordIndex]")
                            endControlFlow()
                            addStatement("return currentWord != 0L")
                            endControlFlow()
                            beginControlFlow("override fun next(): Map.Entry<$enumRef, V>")
                            addStatement("if (!hasNext()) throw NoSuchElementException()")
                            addStatement("val bit = currentWord.countTrailingZeroBits()")
                            addStatement("val ordinal = (currentWordIndex shl 6) + bit")
                            addStatement("currentWord = currentWord and (currentWord - 1)")
                            beginControlFlow("return object : Map.Entry<$enumRef, V>")
                            addStatement("override val key: $enumRef get() = $enumRef.entries[ordinal]")
                            addStatement("override val value: V get() = valueAt(ordinal)")
                            beginControlFlow("override fun equals(other: Any?): Boolean")
                            addStatement("if (other !is Map.Entry<*, *>) return false")
                            addStatement("return key == other.key && value == other.value")
                            endControlFlow()
                            addStatement(
                                "override fun hashCode(): Int = key.hashCode() xor (if (ordinal < slots.size) (slots[ordinal]?.hashCode() ?: 0) else 0)"
                            )
                            endControlFlow()
                            endControlFlow()
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

                                beginControlFlow("if (other is $mutableImplName<*>)")
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
        }

        val mutableEntryTypeRef = ClassName(null, "MutableMap", "MutableEntry")
            .parameterized(keyTypeRef, valueTypeVar)
            .ref()
        val mutableEntriesType = KotlinClassNames.MUTABLE_SET.parameterized(mutableEntryTypeRef)
        val mutableEntriesTypeRef = mutableEntriesType.ref()

        val mutableImpl = KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, mutableImplName) {
            addModifier(KotlinModifier.PRIVATE)
            addDoc("Mutable implementation for [$mutableInterfaceName].")
            addTypeVariable(valueTypeVar)

            primaryConstructor(
                KotlinConstructorSpec {
                    addParameter(
                        KotlinValueParameterSpec("keyWords", wordsTypeRef) {
                            immutableProperty()
                        }
                    )
                    addParameter(
                        KotlinValueParameterSpec("slots", slotsTypeRef) {
                            immutableProperty()
                        }
                    )
                    addParameter(
                        KotlinValueParameterSpec("mapSize", KotlinClassNames.INT.ref()) {
                            addModifier(KotlinModifier.PRIVATE)
                            mutableProperty()
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
                    addCode(
                        CodeValue {
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("val mask = 1L shl (ordinal and 63)")
                            addStatement("return (keyWords[wordIndex] and mask) != 0L")
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

            addFunction(
                KotlinFunctionSpec("copy", mutableTypeRef) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return $mutableImplName(keyWords.copyOf(), slots.copyOf(), mapSize)")
                }
            )

            addProperty(
                KotlinPropertySpec("size", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    getter { addStatement("return mapSize") }
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
                            addStatement("if (slots[ordinal] == value) return true")
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
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("val mask = 1L shl (ordinal and 63)")
                            addStatement("val existed = (keyWords[wordIndex] and mask) != 0L")
                            addStatement("val oldValue = if (existed) valueAt(ordinal) else null")
                            addStatement("slots[ordinal] = value")
                            beginControlFlow("if (!existed)")
                            addStatement("keyWords[wordIndex] = keyWords[wordIndex] or mask")
                            addStatement("mapSize++")
                            endControlFlow()
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
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("val mask = 1L shl (ordinal and 63)")
                            addStatement("if ((keyWords[wordIndex] and mask) == 0L) return null")
                            addStatement("val oldValue = valueAt(ordinal)")
                            addStatement("keyWords[wordIndex] = keyWords[wordIndex] and mask.inv()")
                            addStatement("slots[ordinal] = null")
                            addStatement("mapSize--")
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
                                    addStatement("val sourceWords = from.keyWords")
                                    addStatement("if (sourceWords.isEmpty()) return")
                                    addStatement("val minSize = minOf(sourceWords.size, keyWords.size)")
                                    addStatement("var addedCount = 0")
                                    beginControlFlow("for (wordIndex in 0 until minSize)")
                                    addStatement("val sourceWord = sourceWords[wordIndex]")
                                    addStatement("if (sourceWord == 0L) continue")
                                    addStatement("val oldWord = keyWords[wordIndex]")
                                    addStatement("val mergedWord = oldWord or sourceWord")
                                    beginControlFlow("if (mergedWord != oldWord)")
                                    addStatement("addedCount += (mergedWord xor oldWord).countOneBits()")
                                    addStatement("keyWords[wordIndex] = mergedWord")
                                    endControlFlow()
                                    addStatement("var word = sourceWord")
                                    beginControlFlow("while (word != 0L)")
                                    addStatement("val bit = word.countTrailingZeroBits()")
                                    addStatement("val ordinal = (wordIndex shl 6) + bit")
                                    addStatement("if (ordinal < from.slots.size) slots[ordinal] = from.slots[ordinal]")
                                    addStatement("word = word and (word - 1)")
                                    endControlFlow()
                                    endControlFlow()
                                    addStatement("mapSize += addedCount")
                                    addStatement("return")
                                    endControlFlow()

                                    beginControlFlow("if (from is $mutableImplName<*>)")
                                    addStatement("val sourceWords = from.keyWords")
                                    addStatement("if (sourceWords.isEmpty()) return")
                                    addStatement("val minSize = minOf(sourceWords.size, keyWords.size)")
                                    addStatement("var addedCount = 0")
                                    beginControlFlow("for (wordIndex in 0 until minSize)")
                                    addStatement("val sourceWord = sourceWords[wordIndex]")
                                    addStatement("if (sourceWord == 0L) continue")
                                    addStatement("val oldWord = keyWords[wordIndex]")
                                    addStatement("val mergedWord = oldWord or sourceWord")
                                    beginControlFlow("if (mergedWord != oldWord)")
                                    addStatement("addedCount += (mergedWord xor oldWord).countOneBits()")
                                    addStatement("keyWords[wordIndex] = mergedWord")
                                    endControlFlow()
                                    addStatement("var word = sourceWord")
                                    beginControlFlow("while (word != 0L)")
                                    addStatement("val bit = word.countTrailingZeroBits()")
                                    addStatement("val ordinal = (wordIndex shl 6) + bit")
                                    addStatement("if (ordinal < from.slots.size) slots[ordinal] = from.slots[ordinal]")
                                    addStatement("word = word and (word - 1)")
                                    endControlFlow()
                                    endControlFlow()
                                    addStatement("mapSize += addedCount")
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
                            addStatement("if (mapSize == 0) return")
                            beginControlFlow("for (wordIndex in keyWords.indices)")
                            addStatement("var word = keyWords[wordIndex]")
                            beginControlFlow("while (word != 0L)")
                            addStatement("val bit = word.countTrailingZeroBits()")
                            addStatement("val ordinal = (wordIndex shl 6) + bit")
                            addStatement("if (ordinal < slots.size) slots[ordinal] = null")
                            addStatement("word = word and (word - 1)")
                            endControlFlow()
                            endControlFlow()
                            addStatement("keyWords.fill(0L)")
                            addStatement("mapSize = 0")
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
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("val mask = 1L shl (ordinal and 63)")
                            addStatement("val existed = (keyWords[wordIndex] and mask) != 0L")
                            addStatement("val oldValue = if (existed) slots[ordinal] else null")
                            addStatement("slots[ordinal] = element.value")
                            beginControlFlow("if (!existed)")
                            addStatement("keyWords[wordIndex] = keyWords[wordIndex] or mask")
                            addStatement("mapSize++")
                            endControlFlow()
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

            val mutableIteratorTypeRef =
                ClassName("kotlin.collections", "MutableIterator").parameterized(mutableEntryTypeRef).ref()
            addFunction(
                KotlinFunctionSpec("createIterator", mutableIteratorTypeRef) {
                    addModifier(KotlinModifier.PRIVATE)
                    addCode(
                        CodeValue {
                            beginControlFlow("return object : MutableIterator<MutableMap.MutableEntry<$enumRef, V>>")
                            addStatement("private var currentWordIndex = 0")
                            addStatement("private var currentWord = if (keyWords.isNotEmpty()) keyWords[0] else 0L")
                            addStatement("private var lastOrdinal = -1")
                            beginControlFlow("override fun hasNext(): Boolean")
                            beginControlFlow("while (currentWord == 0L && currentWordIndex < keyWords.lastIndex)")
                            addStatement("currentWordIndex++")
                            addStatement("currentWord = keyWords[currentWordIndex]")
                            endControlFlow()
                            addStatement("return currentWord != 0L")
                            endControlFlow()
                            beginControlFlow(
                                "override fun next(): MutableMap.MutableEntry<$enumRef, V>"
                            )
                            addStatement("if (!hasNext()) throw NoSuchElementException()")
                            addStatement("val bit = currentWord.countTrailingZeroBits()")
                            addStatement("val ordinal = (currentWordIndex shl 6) + bit")
                            addStatement("lastOrdinal = ordinal")
                            addStatement("currentWord = currentWord and (currentWord - 1)")
                            addStatement("return createEntry(ordinal)")
                            endControlFlow()
                            beginControlFlow("override fun remove()")
                            addStatement("check(lastOrdinal >= 0) { \"next() must be called before remove()\" }")
                            addStatement("val ordinal = lastOrdinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("val mask = 1L shl (ordinal and 63)")
                            beginControlFlow("if ((keyWords[wordIndex] and mask) != 0L)")
                            addStatement("keyWords[wordIndex] = keyWords[wordIndex] and mask.inv()")
                            addStatement("slots[ordinal] = null")
                            addStatement("mapSize--")
                            endControlFlow()
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
                            beginControlFlow("return object : MutableMap.MutableEntry<$enumRef, V>")
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
                            addStatement("val leftValue = slots[ordinal]")
                            addStatement("val rightValue = if (ordinal < other.slots.size) other.slots[ordinal] else null")
                            addStatement("if (leftValue != rightValue) return false")
                            addStatement("word = word and (word - 1)")
                            endControlFlow()
                            endControlFlow()
                                addStatement("return true")
                                endControlFlow()

                                beginControlFlow("if (other is $mutableImplName<*>)")
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
                                addStatement("val leftValue = slots[ordinal]")
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
                            addStatement("val valueHash = slots[ordinal]?.hashCode() ?: 0")
                            addStatement("hash += keyHash xor valueHash")
                            addStatement("word = word and (word - 1)")
                            endControlFlow()
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
                lastNonZeroWordIndexFunction,
                trimWordsFunction,
                highestOrdinalPlusOneFunction,
                bitCountOfWordsFunction,
                createImmutableFunction,
                createImmutableCopyFunction,
            ),
            properties = listOf(
                emptyProperty,
            )
        )
    }

}
