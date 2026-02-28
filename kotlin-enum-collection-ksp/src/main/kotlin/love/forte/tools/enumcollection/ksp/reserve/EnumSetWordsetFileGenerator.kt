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
 * Generates wordset-based enum set implementations.
 *
 * Storage is based on `LongArray` and is used when enum entries > 64.
 */
internal object EnumSetWordsetFileGenerator {

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
        val longArrayTypeRef = ClassName("kotlin", "LongArray").ref()

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
        val iterableTypeRef = KotlinClassNames.ITERABLE.parameterized(keyTypeRef).ref()

        val superImmutable = if (inheritApi) API_ENUM_SET.parameterized(keyTypeRef) else setType
        val superMutable = if (inheritApi) API_MUTABLE_ENUM_SET.parameterized(keyTypeRef) else mutableSetType

        val immutableInterface = KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.INTERFACE, immutableInterfaceName) {
            applyVisibility(visibility)
            addDoc("An enum-specialized set optimized for [$enumRef].")
            addSuperinterface(superImmutable)
            if (!inheritApi) {
                addFunction(
                    KotlinFunctionSpec("containsAny", KotlinClassNames.BOOLEAN.ref()) {
                        applyMemberVisibility(visibility)
                        addParameter(KotlinValueParameterSpec("elements", collectionTypeRef))
                    }
                )
                addFunction(
                    KotlinFunctionSpec("intersect", setTypeRef) {
                        applyMemberVisibility(visibility)
                        addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    }
                )
                addFunction(
                    KotlinFunctionSpec("union", setTypeRef) {
                        applyMemberVisibility(visibility)
                        addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    }
                )
                addFunction(
                    KotlinFunctionSpec("difference", setTypeRef) {
                        applyMemberVisibility(visibility)
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
                    applyMemberVisibility(visibility)
                }
            )
        }

        val emptyProperty = KotlinPropertySpec("EMPTY", immutableType.ref()) {
            addModifier(KotlinModifier.PRIVATE)
            initializer("$implName(longArrayOf())")
        }

        val wordSize = (enumSize + 63) ushr 6
        val tail = enumSize and 63

        val createFullWordsFunction = KotlinFunctionSpec("createFullWords", longArrayTypeRef) {
            addModifier(KotlinModifier.PRIVATE)
            addCode(
                CodeValue {
                    addStatement("val words = LongArray($wordSize) { -1L }")
                    if (tail != 0) {
                        addStatement("words[${wordSize - 1}] = (1L shl $tail) - 1L")
                    }
                    addStatement("return words")
                }
            )
        }

        val fullProperty = KotlinPropertySpec("FULL", immutableType.ref()) {
            addModifier(KotlinModifier.PRIVATE)
            initializer("$implName(createFullWords())")
        }

        val createImmutableFunction = KotlinFunctionSpec("createEnumSet", immutableType.ref()) {
            addModifier(KotlinModifier.PRIVATE)
            addParameter(KotlinValueParameterSpec("words", longArrayTypeRef))
            addCode(
                CodeValue {
                    addStatement("val lastNonZeroWordIndex = lastNonZeroIndex(words)")
                    addStatement("if (lastNonZeroWordIndex < 0) return EMPTY")
                    addStatement("return $implName(trimByLastNonZero(words, lastNonZeroWordIndex))")
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
                    addStatement("is $implName -> $mutableImplName(bs.copyOf())")
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
                    addStatement("is $mutableImplName -> createEnumSet(bs.copyOf())")
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
                    addStatement("var maxOrdinal = 0")
                    beginControlFlow("for (entry in entries)")
                    addStatement("val ordinal = entry.ordinal")
                    addStatement("if (ordinal > maxOrdinal) maxOrdinal = ordinal")
                    addStatement("if (maxOrdinal == ${enumSize - 1}) break")
                    endControlFlow()
                    addStatement("val maxWordSize = $wordSize")
                    addStatement("val wordSize = minOf(maxWordSize, (maxOrdinal + 64) ushr 6)")
                    addStatement("val words = LongArray(wordSize)")
                    beginControlFlow("for (entry in entries)")
                    addStatement("val ordinal = entry.ordinal")
                    addStatement("val wordIndex = ordinal ushr 6")
                    addStatement("words[wordIndex] = words[wordIndex] or (1L shl (ordinal and 63))")
                    endControlFlow()
                    addStatement("return $implName(words)")
                }
            )
        }

        val factoryImmutableIterable = KotlinFunctionSpec(immutableInterfaceName, immutableType.ref()) {
            applyVisibility(visibility)
            addParameter(KotlinValueParameterSpec("entries", iterableTypeRef))
            addDoc("Creates an immutable set containing [entries].")
            addCode(
                CodeValue {
                    addStatement("var words = LongArray(0)")
                    beginControlFlow("for (entry in entries)")
                    addStatement("val ordinal = entry.ordinal")
                    addStatement("val wordIndex = ordinal ushr 6")
                    beginControlFlow("if (wordIndex >= words.size)")
                    addStatement("words = words.copyOf(wordIndex + 1)")
                    endControlFlow()
                    addStatement("words[wordIndex] = words[wordIndex] or (1L shl (ordinal and 63))")
                    endControlFlow()
                    addStatement("return createEnumSet(words)")
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
            addStatement("return $mutableImplName(if (full) createFullWords() else longArrayOf())")
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
                    addStatement("var words = LongArray(0)")
                    beginControlFlow("for (entry in entries)")
                    addStatement("val ordinal = entry.ordinal")
                    addStatement("val wordIndex = ordinal ushr 6")
                    beginControlFlow("if (wordIndex >= words.size)")
                    addStatement("words = words.copyOf(wordIndex + 1)")
                    endControlFlow()
                    addStatement("words[wordIndex] = words[wordIndex] or (1L shl (ordinal and 63))")
                    endControlFlow()
                    addStatement("return $mutableImplName(words)")
                }
            )
        }

        val factoryMutableIterable = KotlinFunctionSpec(mutableInterfaceName, mutableType.ref()) {
            applyVisibility(visibility)
            addParameter(KotlinValueParameterSpec("entries", iterableTypeRef))
            addDoc("Creates a mutable set containing [entries].")
            addCode(
                CodeValue {
                    addStatement("var words = LongArray(0)")
                    beginControlFlow("for (entry in entries)")
                    addStatement("val ordinal = entry.ordinal")
                    addStatement("val wordIndex = ordinal ushr 6")
                    beginControlFlow("if (wordIndex >= words.size)")
                    addStatement("words = words.copyOf(wordIndex + 1)")
                    endControlFlow()
                    addStatement("words[wordIndex] = words[wordIndex] or (1L shl (ordinal and 63))")
                    endControlFlow()
                    addStatement("return $mutableImplName(words)")
                }
            )
        }

        val iteratorType = ClassName("kotlin.collections", "Iterator").parameterized(keyTypeRef).ref()
        val mutableIteratorType = ClassName("kotlin.collections", "MutableIterator").parameterized(keyTypeRef).ref()
        val anyNullableRef = KotlinClassNames.ANY.ref { kotlinStatus { nullable = true } }

        val bitCountFunction = KotlinFunctionSpec("bitCountOf", KotlinClassNames.INT.ref()) {
            addModifier(KotlinModifier.PRIVATE)
            addParameter(KotlinValueParameterSpec("words", longArrayTypeRef))
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

        val lastNonZeroIndexFunction = KotlinFunctionSpec("lastNonZeroIndex", KotlinClassNames.INT.ref()) {
            addModifier(KotlinModifier.PRIVATE)
            addParameter(KotlinValueParameterSpec("words", longArrayTypeRef))
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

        val trimByLastNonZeroFunction = KotlinFunctionSpec("trimByLastNonZero", longArrayTypeRef) {
            addModifier(KotlinModifier.PRIVATE)
            addParameter(KotlinValueParameterSpec("words", longArrayTypeRef))
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

	    	val immutableImpl = KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, implName) {
	    	    addModifier(KotlinModifier.PRIVATE)
	    	    addDoc("Immutable implementation for [$immutableInterfaceName].")
	    	    primaryConstructor(
	    	        KotlinConstructorSpec {
	    	            addParameter(
	    	                KotlinValueParameterSpec("bs", longArrayTypeRef) {
	    	                    immutableProperty()
	    	                }
	    	            )
	    	        }
	    	    )

	    	    addSuperinterface(immutableType)

            addFunction(
                KotlinFunctionSpec("contains", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("element", keyTypeRef))
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
	    	                    beginControlFlow("val otherWords = when (elements)")
	    	                    addStatement("is $implName -> elements.bs")
	    	                    addStatement("is $mutableImplName -> elements.bs")
	    	                    addStatement("else -> null")
	    	                    endControlFlow()
	    	                    beginControlFlow("if (otherWords != null)")
	    	                    addStatement("if (otherWords.size > bs.size) return false")
	    	                    beginControlFlow("for (wordIndex in otherWords.indices)")
	    	                    addStatement("val otherWord = otherWords[wordIndex]")
	    	                    addStatement("if ((bs[wordIndex] and otherWord) != otherWord) return false")
	    	                    endControlFlow()
	    	                    addStatement("return true")
	    	                    endControlFlow()
	    	                    addStatement("if (bs.isEmpty()) return false")
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
	    	                    addStatement("if (elements.isEmpty() || bs.isEmpty()) return false")
	    	                    beginControlFlow("val otherWords = when (elements)")
	    	                    addStatement("is $implName -> elements.bs")
	    	                    addStatement("is $mutableImplName -> elements.bs")
	    	                    addStatement("else -> null")
	    	                    endControlFlow()
	    	                    beginControlFlow("if (otherWords != null)")
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
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
	    	                CodeValue {
	    	                    addStatement("if (bs.isEmpty() || other.isEmpty()) return EMPTY")
	    	                    addStatement("if (other === this) return this")
	    	                    beginControlFlow("val otherWords = when (other)")
	    	                    addStatement("is $implName -> other.bs")
	    	                    addStatement("is $mutableImplName -> other.bs")
	    	                    addStatement("else -> null")
	    	                    endControlFlow()
	    	                    beginControlFlow("if (otherWords != null)")
	    	                    addStatement("val minSize = minOf(bs.size, otherWords.size)")
	    	                    addStatement("val resultWords = LongArray(minSize)")
	    	                    addStatement("var lastNonZeroWordIndex = -1")
	    	                    beginControlFlow("for (wordIndex in 0 until minSize)")
                            addStatement("val word = bs[wordIndex] and otherWords[wordIndex]")
                            addStatement("resultWords[wordIndex] = word")
                            addStatement("if (word != 0L) lastNonZeroWordIndex = wordIndex")
                            endControlFlow()
                            addStatement("return createEnumSet(trimByLastNonZero(resultWords, lastNonZeroWordIndex))")
                            endControlFlow()
                            addStatement("val currentWords = bs")
                            addStatement("val resultWords = LongArray(currentWords.size)")
                            addStatement("var lastNonZeroWordIndex = -1")
                            beginControlFlow("if (other.size < size)")
                            beginControlFlow("for (element in other)")
                            addStatement("val ordinal = element.ordinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("if (wordIndex >= currentWords.size) continue")
                            addStatement("val bitMask = 1L shl (ordinal and 63)")
                            addStatement("if ((currentWords[wordIndex] and bitMask) == 0L) continue")
                            addStatement("resultWords[wordIndex] = resultWords[wordIndex] or bitMask")
                            addStatement("if (wordIndex > lastNonZeroWordIndex) lastNonZeroWordIndex = wordIndex")
                            endControlFlow()
                            endControlFlow()
                            beginControlFlow("else")
                            beginControlFlow("for (wordIndex in currentWords.indices)")
                            addStatement("var word = currentWords[wordIndex]")
                            beginControlFlow("while (word != 0L)")
                            addStatement("val bit = word.countTrailingZeroBits()")
                            addStatement("if (other.contains($enumRef.entries[(wordIndex shl 6) + bit])) {")
                            addStatement("    resultWords[wordIndex] = resultWords[wordIndex] or (1L shl bit)")
                            addStatement("    lastNonZeroWordIndex = wordIndex")
                            addStatement("}")
                            addStatement("word = word and (word - 1)")
                            endControlFlow()
                            endControlFlow()
                            endControlFlow()
                            addStatement("return createEnumSet(trimByLastNonZero(resultWords, lastNonZeroWordIndex))")
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
	    	                    addStatement("if (other.isEmpty()) return this")
	    	                    addStatement("if (other === this) return this")
	    	                    beginControlFlow("val otherWords = when (other)")
	    	                    addStatement("is $implName -> other.bs")
	    	                    addStatement("is $mutableImplName -> other.bs")
	    	                    addStatement("else -> null")
	    	                    endControlFlow()
	    	                    beginControlFlow("if (otherWords != null)")
	    	                    addStatement("val currentWords = bs")
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
                            addStatement("return createEnumSet(resultWords)")
                            endControlFlow()
                            addStatement("val resultWords = otherWords.copyOf()")
                            addStatement("var changed = false")
                            beginControlFlow("for (wordIndex in currentWords.indices)")
                            addStatement("val oldWord = resultWords[wordIndex]")
                            addStatement("val mergedWord = oldWord or currentWords[wordIndex]")
                            beginControlFlow("if (mergedWord != oldWord)")
                            addStatement("resultWords[wordIndex] = mergedWord")
                            addStatement("changed = true")
                            endControlFlow()
                            endControlFlow()
                            addStatement("if (!changed) return this")
                            addStatement("return createEnumSet(resultWords)")
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
                            addStatement("return createEnumSet(resultWords)")
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
	    	                    addStatement("if (bs.isEmpty() || other.isEmpty()) return this")
	    	                    addStatement("if (other === this) return EMPTY")
	    	                    beginControlFlow("val otherWords = when (other)")
	    	                    addStatement("is $implName -> other.bs")
	    	                    addStatement("is $mutableImplName -> other.bs")
	    	                    addStatement("else -> null")
	    	                    endControlFlow()
	    	                    beginControlFlow("if (otherWords != null)")
	    	                    addStatement("val resultWords = bs.copyOf()")
	    	                    addStatement("val minSize = minOf(resultWords.size, otherWords.size)")
	    	                    addStatement("var changed = false")
	    	                    addStatement("var lastNonZeroWordIndex = -1")
                            addStatement("var wordIndex = 0")
                            beginControlFlow("while (wordIndex < minSize)")
                            addStatement("val oldWord = resultWords[wordIndex]")
                            addStatement("val newWord = oldWord and otherWords[wordIndex].inv()")
                            beginControlFlow("if (newWord != oldWord)")
                            addStatement("resultWords[wordIndex] = newWord")
                            addStatement("changed = true")
                            endControlFlow()
                            addStatement("if (resultWords[wordIndex] != 0L) lastNonZeroWordIndex = wordIndex")
                            addStatement("wordIndex++")
                            endControlFlow()
                            beginControlFlow("while (wordIndex < resultWords.size)")
                            addStatement("if (resultWords[wordIndex] != 0L) lastNonZeroWordIndex = wordIndex")
                            addStatement("wordIndex++")
                            endControlFlow()
                            addStatement("if (!changed) return this")
                            addStatement("return createEnumSet(trimByLastNonZero(resultWords, lastNonZeroWordIndex))")
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
                            addStatement("return createEnumSet(trimByLastNonZero(resultWords, lastNonZeroIndex(resultWords)))")
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
                KotlinFunctionSpec("iterator", iteratorType) {
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

            addProperty(
                KotlinPropertySpec("size", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    getter { addStatement("return bitCountOf(bs)") }
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
	    	                    beginControlFlow("val rightWords = when (other)")
	    	                    addStatement("is $implName -> other.bs")
	    	                    addStatement("is $mutableImplName -> other.bs")
	    	                    addStatement("else -> null")
	    	                    endControlFlow()
	    	                    beginControlFlow("if (rightWords != null)")
	    	                    addStatement("val leftWords = bs")
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
                            addStatement("return true")
                            endControlFlow()
                            addStatement("if (other.size != bitCountOf(bs)) return false")
                            beginControlFlow("for (element in other)")
                            addStatement("if (element !is $enumRef) return false")
                            addStatement("val ordinal = element.ordinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("if (wordIndex >= bs.size) return false")
                            addStatement("if ((bs[wordIndex] and (1L shl (ordinal and 63))) == 0L) return false")
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
        }

	    	val mutableImpl = KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, mutableImplName) {
	    	    addModifier(KotlinModifier.PRIVATE)
	    	    addDoc("Mutable implementation for [$mutableInterfaceName].")
	    	    primaryConstructor(
	    	        KotlinConstructorSpec {
	    	            addParameter(
	    	                KotlinValueParameterSpec("bs", longArrayTypeRef) {
	    	                    mutableProperty()
	    	                }
	    	            )
	    	        }
	    	    )

	    	    addSuperinterface(mutableType)

            addFunction(
                KotlinFunctionSpec("copy", mutableType.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("return $mutableImplName(bs.copyOf())")
                }
            )

            addFunction(
                KotlinFunctionSpec("contains", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("element", keyTypeRef))
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
	    	                    beginControlFlow("val otherWords = when (elements)")
	    	                    addStatement("is $implName -> elements.bs")
	    	                    addStatement("is $mutableImplName -> elements.bs")
	    	                    addStatement("else -> null")
	    	                    endControlFlow()
	    	                    beginControlFlow("if (otherWords != null)")
	    	                    addStatement("if (otherWords.size > bs.size) return false")
	    	                    beginControlFlow("for (wordIndex in otherWords.indices)")
	    	                    addStatement("val otherWord = otherWords[wordIndex]")
	    	                    addStatement("if ((bs[wordIndex] and otherWord) != otherWord) return false")
	    	                    endControlFlow()
	    	                    addStatement("return true")
	    	                    endControlFlow()
	    	                    addStatement("if (bs.isEmpty()) return false")
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
	    	                    addStatement("if (elements.isEmpty() || bs.isEmpty()) return false")
	    	                    beginControlFlow("val otherWords = when (elements)")
	    	                    addStatement("is $implName -> elements.bs")
	    	                    addStatement("is $mutableImplName -> elements.bs")
	    	                    addStatement("else -> null")
	    	                    endControlFlow()
	    	                    beginControlFlow("if (otherWords != null)")
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

            // Immutable operations return immutable snapshots (never `this`).
            addFunction(
                KotlinFunctionSpec("intersect", setTypeRef) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("other", setTypeRef))
                    addCode(
	    	                CodeValue {
	    	                    addStatement("if (bs.isEmpty() || other.isEmpty()) return EMPTY")
	    	                    addStatement("if (other === this) return createEnumSet(bs.copyOf())")
	    	                    beginControlFlow("val otherWords = when (other)")
	    	                    addStatement("is $implName -> other.bs")
	    	                    addStatement("is $mutableImplName -> other.bs")
	    	                    addStatement("else -> null")
	    	                    endControlFlow()
	    	                    beginControlFlow("if (otherWords != null)")
	    	                    addStatement("val minSize = minOf(bs.size, otherWords.size)")
	    	                    addStatement("val resultWords = LongArray(minSize)")
	    	                    addStatement("var lastNonZeroWordIndex = -1")
	    	                    beginControlFlow("for (wordIndex in 0 until minSize)")
                            addStatement("val word = bs[wordIndex] and otherWords[wordIndex]")
                            addStatement("resultWords[wordIndex] = word")
                            addStatement("if (word != 0L) lastNonZeroWordIndex = wordIndex")
                            endControlFlow()
                            addStatement("return createEnumSet(trimByLastNonZero(resultWords, lastNonZeroWordIndex))")
                            endControlFlow()
                            addStatement("val currentWords = bs")
                            addStatement("val resultWords = LongArray(currentWords.size)")
                            addStatement("var lastNonZeroWordIndex = -1")
                            beginControlFlow("if (other.size < size)")
                            beginControlFlow("for (element in other)")
                            addStatement("val ordinal = element.ordinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("if (wordIndex >= currentWords.size) continue")
                            addStatement("val bitMask = 1L shl (ordinal and 63)")
                            addStatement("if ((currentWords[wordIndex] and bitMask) == 0L) continue")
                            addStatement("resultWords[wordIndex] = resultWords[wordIndex] or bitMask")
                            addStatement("if (wordIndex > lastNonZeroWordIndex) lastNonZeroWordIndex = wordIndex")
                            endControlFlow()
                            endControlFlow()
                            beginControlFlow("else")
                            beginControlFlow("for (wordIndex in currentWords.indices)")
                            addStatement("var word = currentWords[wordIndex]")
                            beginControlFlow("while (word != 0L)")
                            addStatement("val bit = word.countTrailingZeroBits()")
                            addStatement("if (other.contains($enumRef.entries[(wordIndex shl 6) + bit])) {")
                            addStatement("    resultWords[wordIndex] = resultWords[wordIndex] or (1L shl bit)")
                            addStatement("    lastNonZeroWordIndex = wordIndex")
                            addStatement("}")
                            addStatement("word = word and (word - 1)")
                            endControlFlow()
                            endControlFlow()
                            endControlFlow()
                            addStatement("return createEnumSet(trimByLastNonZero(resultWords, lastNonZeroWordIndex))")
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
	    	                    addStatement("if (other.isEmpty()) return createEnumSet(bs.copyOf())")
	    	                    addStatement("if (other === this) return createEnumSet(bs.copyOf())")
	    	                    beginControlFlow("val otherWords = when (other)")
	    	                    addStatement("is $implName -> other.bs")
	    	                    addStatement("is $mutableImplName -> other.bs")
	    	                    addStatement("else -> null")
	    	                    endControlFlow()
	    	                    beginControlFlow("if (otherWords != null)")
	    	                    addStatement("val maxSize = maxOf(bs.size, otherWords.size)")
	    	                    addStatement("val resultWords = LongArray(maxSize)")
	    	                    beginControlFlow("for (wordIndex in 0 until maxSize)")
	    	                    addStatement("val leftWord = if (wordIndex < bs.size) bs[wordIndex] else 0L")
                            addStatement("val rightWord = if (wordIndex < otherWords.size) otherWords[wordIndex] else 0L")
                            addStatement("resultWords[wordIndex] = leftWord or rightWord")
                            endControlFlow()
                            addStatement("return createEnumSet(resultWords)")
                            endControlFlow()
                            addStatement("var resultWords = bs.copyOf()")
                            beginControlFlow("for (element in other)")
                            addStatement("val ordinal = element.ordinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            beginControlFlow("if (wordIndex >= resultWords.size)")
                            addStatement("resultWords = resultWords.copyOf(wordIndex + 1)")
                            endControlFlow()
                            addStatement("resultWords[wordIndex] = resultWords[wordIndex] or (1L shl (ordinal and 63))")
                            endControlFlow()
                            addStatement("return createEnumSet(resultWords)")
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
	    	                    addStatement("if (bs.isEmpty() || other.isEmpty()) return createEnumSet(bs.copyOf())")
	    	                    addStatement("if (other === this) return EMPTY")
	    	                    beginControlFlow("val otherWords = when (other)")
	    	                    addStatement("is $implName -> other.bs")
	    	                    addStatement("is $mutableImplName -> other.bs")
	    	                    addStatement("else -> null")
	    	                    endControlFlow()
	    	                    beginControlFlow("if (otherWords != null)")
	    	                    addStatement("val resultWords = bs.copyOf()")
	    	                    addStatement("val minSize = minOf(resultWords.size, otherWords.size)")
	    	                    addStatement("var lastNonZeroWordIndex = -1")
	    	                    addStatement("var wordIndex = 0")
                            beginControlFlow("while (wordIndex < minSize)")
                            addStatement("resultWords[wordIndex] = resultWords[wordIndex] and otherWords[wordIndex].inv()")
                            addStatement("if (resultWords[wordIndex] != 0L) lastNonZeroWordIndex = wordIndex")
                            addStatement("wordIndex++")
                            endControlFlow()
                            beginControlFlow("while (wordIndex < resultWords.size)")
                            addStatement("if (resultWords[wordIndex] != 0L) lastNonZeroWordIndex = wordIndex")
                            addStatement("wordIndex++")
                            endControlFlow()
                            addStatement("return createEnumSet(trimByLastNonZero(resultWords, lastNonZeroWordIndex))")
                            endControlFlow()
                            addStatement("val resultWords = bs.copyOf()")
                            beginControlFlow("for (element in other)")
                            addStatement("val ordinal = element.ordinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("if (wordIndex >= resultWords.size) continue")
                            addStatement("resultWords[wordIndex] = resultWords[wordIndex] and (1L shl (ordinal and 63)).inv()")
                            endControlFlow()
                            addStatement("return createEnumSet(trimByLastNonZero(resultWords, lastNonZeroIndex(resultWords)))")
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
                KotlinFunctionSpec("iterator", mutableIteratorType) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addCode(
                        CodeValue {
                            beginControlFlow("return object : MutableIterator<$enumRef>")
                            addStatement("private var currentWordIndex = 0")
                            addStatement("private var currentWord = if (bs.isNotEmpty()) bs[0] else 0L")
                            addStatement("private var lastReturnedWordIndex = -1")
                            addStatement("private var lastReturnedBit = -1")
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
                            addStatement("lastReturnedWordIndex = currentWordIndex")
                            addStatement("lastReturnedBit = bit")
                            addStatement("currentWord = currentWord and (currentWord - 1)")
                            addStatement("return $enumRef.entries[(currentWordIndex shl 6) + bit]")
                            endControlFlow()
                            beginControlFlow("override fun remove()")
                            addStatement("check(lastReturnedWordIndex >= 0) { \"next() must be called before remove()\" }")
                            addStatement("val wordIndex = lastReturnedWordIndex")
                            addStatement("val newWord = bs[wordIndex] and (1L shl lastReturnedBit).inv()")
                            addStatement("bs[wordIndex] = newWord")
                            beginControlFlow("if (wordIndex == bs.lastIndex && newWord == 0L)")
                            addStatement("bs = trimByLastNonZero(bs, lastNonZeroIndex(bs))")
                            beginControlFlow("if (currentWordIndex >= bs.size)")
                            addStatement("currentWordIndex = bs.size")
                            addStatement("currentWord = 0L")
                            endControlFlow()
                            endControlFlow()
                            addStatement("lastReturnedWordIndex = -1")
                            addStatement("lastReturnedBit = -1")
                            endControlFlow()
                            endControlFlow()
                        }
                    )
                }
            )

            addProperty(
                KotlinPropertySpec("size", KotlinClassNames.INT.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    getter { addStatement("return bitCountOf(bs)") }
                }
            )

            addFunction(
                KotlinFunctionSpec("add", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("element", keyTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val ordinal = element.ordinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            beginControlFlow("if (wordIndex >= bs.size)")
                            addStatement("bs = bs.copyOf(wordIndex + 1)")
                            endControlFlow()
                            addStatement("val oldWord = bs[wordIndex]")
                            addStatement("val newWord = oldWord or (1L shl (ordinal and 63))")
                            addStatement("bs[wordIndex] = newWord")
                            addStatement("return newWord != oldWord")
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
	    	                    beginControlFlow("val otherWords = when (elements)")
	    	                    addStatement("is $implName -> elements.bs")
	    	                    addStatement("is $mutableImplName -> elements.bs")
	    	                    addStatement("else -> null")
	    	                    endControlFlow()
	    	                    beginControlFlow("if (otherWords != null)")
	    	                    addStatement("if (otherWords.isEmpty()) return false")
	    	                    beginControlFlow("if (otherWords.size > bs.size)")
	    	                    addStatement("bs = bs.copyOf(otherWords.size)")
	    	                    endControlFlow()
                            addStatement("var modified = false")
                            beginControlFlow("for (wordIndex in otherWords.indices)")
                            addStatement("val oldWord = bs[wordIndex]")
                            addStatement("val newWord = oldWord or otherWords[wordIndex]")
                            addStatement("if (newWord != oldWord) modified = true")
                            addStatement("bs[wordIndex] = newWord")
                            endControlFlow()
                            addStatement("return modified")
                            endControlFlow()
                            addStatement("var modified = false")
                            beginControlFlow("for (element in elements)")
                            addStatement("if (add(element)) modified = true")
                            endControlFlow()
                            addStatement("return modified")
                        }
                    )
                }
            )

            addFunction(
                KotlinFunctionSpec("clear") {
                    addModifier(KotlinModifier.OVERRIDE)
                    addStatement("bs = LongArray(0)")
                }
            )

            addFunction(
                KotlinFunctionSpec("remove", KotlinClassNames.BOOLEAN.ref()) {
                    addModifier(KotlinModifier.OVERRIDE)
                    addParameter(KotlinValueParameterSpec("element", keyTypeRef))
                    addCode(
                        CodeValue {
                            addStatement("val ordinal = element.ordinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("if (wordIndex >= bs.size) return false")
                            addStatement("val oldWord = bs[wordIndex]")
                            addStatement("val newWord = oldWord and (1L shl (ordinal and 63)).inv()")
                            addStatement("if (newWord == oldWord) return false")
                            addStatement("bs[wordIndex] = newWord")
                            beginControlFlow("if (wordIndex == bs.lastIndex && newWord == 0L)")
                            addStatement("bs = trimByLastNonZero(bs, lastNonZeroIndex(bs))")
                            endControlFlow()
                            addStatement("return true")
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
	    	                    addStatement("if (elements.isEmpty() || bs.isEmpty()) return false")
	    	                    beginControlFlow("val otherWords = when (elements)")
	    	                    addStatement("is $implName -> elements.bs")
	    	                    addStatement("is $mutableImplName -> elements.bs")
	    	                    addStatement("else -> null")
	    	                    endControlFlow()
	    	                    beginControlFlow("if (otherWords != null)")
	    	                    addStatement("val minSize = minOf(bs.size, otherWords.size)")
	    	                    addStatement("var modified = false")
	    	                    beginControlFlow("for (wordIndex in 0 until minSize)")
	    	                    addStatement("val oldWord = bs[wordIndex]")
                            addStatement("val newWord = oldWord and otherWords[wordIndex].inv()")
                            beginControlFlow("if (newWord != oldWord)")
                            addStatement("bs[wordIndex] = newWord")
                            addStatement("modified = true")
                            endControlFlow()
                            endControlFlow()
                            addStatement("if (!modified) return false")
                            addStatement("bs = trimByLastNonZero(bs, lastNonZeroIndex(bs))")
                            addStatement("return true")
                            endControlFlow()
                            addStatement("val oldWords = bs")
                            addStatement("val resultWords = oldWords.copyOf()")
                            addStatement("var modified = false")
                            beginControlFlow("for (element in elements)")
                            addStatement("val ordinal = element.ordinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("if (wordIndex >= resultWords.size) continue")
                            addStatement("val oldWord = resultWords[wordIndex]")
                            addStatement("val newWord = oldWord and (1L shl (ordinal and 63)).inv()")
                            beginControlFlow("if (newWord != oldWord)")
                            addStatement("resultWords[wordIndex] = newWord")
                            addStatement("modified = true")
                            endControlFlow()
                            endControlFlow()
                            addStatement("if (!modified) return false")
                            addStatement("bs = trimByLastNonZero(resultWords, lastNonZeroIndex(resultWords))")
                            addStatement("return true")
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
	    	                    addStatement("if (bs.isEmpty()) return false")
	    	                    beginControlFlow("val otherWords = when (elements)")
	    	                    addStatement("is $implName -> elements.bs")
	    	                    addStatement("is $mutableImplName -> elements.bs")
	    	                    addStatement("else -> null")
	    	                    endControlFlow()
	    	                    beginControlFlow("if (otherWords != null)")
	    	                    addStatement("if (otherWords.isEmpty()) {")
	    	                    addStatement("    bs = LongArray(0)")
	    	                    addStatement("    return true")
	    	                    addStatement("}")
                            addStatement("val minSize = minOf(bs.size, otherWords.size)")
                            addStatement("val resultWords = LongArray(minSize)")
                            addStatement("var modified = false")
                            addStatement("var lastNonZeroWordIndex = -1")
                            beginControlFlow("for (wordIndex in 0 until minSize)")
                            addStatement("val word = bs[wordIndex] and otherWords[wordIndex]")
                            addStatement("resultWords[wordIndex] = word")
                            addStatement("if (word != 0L) lastNonZeroWordIndex = wordIndex")
                            addStatement("if (word != bs[wordIndex]) modified = true")
                            endControlFlow()
                            addStatement("if (!modified && bs.size == minSize) return false")
                            addStatement("bs = trimByLastNonZero(resultWords, lastNonZeroWordIndex)")
                            addStatement("return true")
                            endControlFlow()
                            beginControlFlow("if (elements.isEmpty())")
                            addStatement("bs = LongArray(0)")
                            addStatement("return true")
                            endControlFlow()
                            addStatement("val currentWords = bs")
                            addStatement("val resultWords = LongArray(currentWords.size)")
                            addStatement("var lastNonZeroWordIndex = -1")
                            beginControlFlow("for (element in elements)")
                            addStatement("val ordinal = element.ordinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("if (wordIndex >= currentWords.size) continue")
                            addStatement("val bitMask = 1L shl (ordinal and 63)")
                            beginControlFlow("if ((currentWords[wordIndex] and bitMask) != 0L)")
                            addStatement("resultWords[wordIndex] = resultWords[wordIndex] or bitMask")
                            addStatement("if (wordIndex > lastNonZeroWordIndex) lastNonZeroWordIndex = wordIndex")
                            endControlFlow()
                            endControlFlow()
                            addStatement("val trimmed = trimByLastNonZero(resultWords, lastNonZeroWordIndex)")
                            addStatement("if (trimmed.contentEquals(currentWords)) return false")
                            addStatement("bs = trimmed")
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
	    	                    beginControlFlow("val rightWords = when (other)")
	    	                    addStatement("is $implName -> other.bs")
	    	                    addStatement("is $mutableImplName -> other.bs")
	    	                    addStatement("else -> null")
	    	                    endControlFlow()
	    	                    beginControlFlow("if (rightWords != null)")
	    	                    addStatement("val leftWords = bs")
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
                            addStatement("return true")
                            endControlFlow()
                            addStatement("if (other.size != bitCountOf(bs)) return false")
                            beginControlFlow("for (element in other)")
                            addStatement("if (element !is $enumRef) return false")
                            addStatement("val ordinal = element.ordinal")
                            addStatement("val wordIndex = ordinal ushr 6")
                            addStatement("if (wordIndex >= bs.size) return false")
                            addStatement("if ((bs[wordIndex] and (1L shl (ordinal and 63))) == 0L) return false")
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
                factoryImmutableEmptyOrFull,
                factoryImmutableVararg,
                factoryImmutableIterable,
                factoryMutableEmptyOrFull,
                factoryMutableVararg,
                factoryMutableIterable,
                createImmutableFunction,
                createFullWordsFunction,
                bitCountFunction,
                lastNonZeroIndexFunction,
                trimByLastNonZeroFunction,
            ),
            properties = listOf(
                emptyProperty,
                fullProperty,
            )
        )
    }

}
