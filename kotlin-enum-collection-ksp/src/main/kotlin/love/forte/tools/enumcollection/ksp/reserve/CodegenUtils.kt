package love.forte.tools.enumcollection.ksp.reserve

import love.forte.codegentle.common.naming.ClassName
import love.forte.codegentle.kotlin.KotlinModifier
import love.forte.codegentle.kotlin.KotlinModifierCollector
import love.forte.codegentle.kotlin.spec.KotlinFunctionSpec
import love.forte.codegentle.kotlin.spec.KotlinPropertySpec
import love.forte.tools.enumcollection.ksp.configuration.InheritanceMode

internal val API_ENUM_SET: ClassName = ClassName("love.forte.tools.enumcollection.api", "EnumSet")
internal val API_ENUM_MAP: ClassName = ClassName("love.forte.tools.enumcollection.api", "EnumMap")

internal val API_MUTABLE_ENUM_SET: ClassName = ClassName("love.forte.tools.enumcollection.api", "MutableEnumSet")
internal val API_MUTABLE_ENUM_MAP: ClassName = ClassName("love.forte.tools.enumcollection.api", "MutableEnumMap")

internal fun KotlinModifierCollector<*>.applyVisibility(visibility: String) {
    when (visibility.uppercase()) {
        "PUBLIC" -> addModifier(KotlinModifier.PUBLIC)
        "PRIVATE" -> addModifier(KotlinModifier.PRIVATE)
        else -> addModifier(KotlinModifier.INTERNAL)
    }
}

internal fun KotlinModifierCollector<*>.applyMemberVisibility(visibility: String) {
    if (visibility.equals(KotlinModifier.PUBLIC.name, ignoreCase = true)) {
        addModifier(KotlinModifier.PUBLIC)
    }
}

internal fun shouldInheritApiType(inheritanceMode: InheritanceMode, inheritanceAvailable: Boolean): Boolean =
    when (inheritanceMode) {
        InheritanceMode.ALWAYS -> true
        InheritanceMode.AUTO -> inheritanceAvailable
        InheritanceMode.NEVER -> false
    }

internal fun KotlinFunctionSpec.Builder.overrideIf(condition: Boolean): KotlinFunctionSpec.Builder = apply {
    if (condition) addModifier(KotlinModifier.OVERRIDE)
}

internal fun KotlinPropertySpec.Builder.overrideIf(condition: Boolean): KotlinPropertySpec.Builder = apply {
    if (condition) addModifier(KotlinModifier.OVERRIDE)
}
