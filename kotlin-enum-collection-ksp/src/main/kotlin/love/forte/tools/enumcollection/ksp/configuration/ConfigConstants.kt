package love.forte.tools.enumcollection.ksp.configuration

internal object ConfigConstants {
    const val CONFIG_PREFIX = "love.forte.tools.enumcollection"

    const val ENUM_SETS_KEY = "enumSets"
    const val ENUM_MAPS_KEY = "enumMaps"
    const val INHERITANCE_MODE_KEY = "inheritanceMode"
    const val BASE_PACKAGE_KEY = "basePackage"
    const val BASE_VISIBILITY_KEY = "baseVisibility"

    const val NAME_SUFFIX = ".name"
    const val VISIBILITY_SUFFIX = ".visibility"
    const val PACKAGE_SUFFIX = ".package"
}

internal fun Map<String, String>.resolveOptionsToConfiguration(): KecConfiguration {
    val enumSets = resolveCsvOption(ConfigConstants.ENUM_SETS_KEY)
    val enumMaps = resolveCsvOption(ConfigConstants.ENUM_MAPS_KEY)
    val externalEnumOverrides = resolveExternalEnumOverrides()

    val inheritanceMode = resolveOption(ConfigConstants.INHERITANCE_MODE_KEY)
        ?.let { InheritanceMode.valueOf(it.replace('-', '_').uppercase()) }

    val basePackage = resolveOption(ConfigConstants.BASE_PACKAGE_KEY)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    val baseVisibility = resolveOption(ConfigConstants.BASE_VISIBILITY_KEY)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.uppercase()
        ?: KecConfiguration.DEFAULT_BASE_VISIBILITY

    return KecConfiguration(
        inheritanceMode = inheritanceMode ?: KecConfiguration.DEFAULT_INHERITANCE_MODE,
        basePackage = basePackage,
        baseVisibility = baseVisibility,
        externalEnumConfigurations = resolveExternalEnumConfigurations(
            enumSets = enumSets,
            enumMaps = enumMaps,
            externalEnumOverrides = externalEnumOverrides,
        )
    )
}

private fun Map<String, String>.resolveOption(key: String): String? {
    return get("${ConfigConstants.CONFIG_PREFIX}.$key")
}

private fun Map<String, String>.resolveCsvOption(key: String): Set<String> {
    return resolveOption(key)?.splitToSequence(',')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.toSet()
        ?: emptySet()
}

private fun Map<String, String>.resolveExternalEnumOverrides(): Map<String, ExternalEnumOverride> {
    val prefix = "${ConfigConstants.CONFIG_PREFIX}."
    val mutableConfigurations = mutableMapOf<String, ExternalEnumOverride>()

    for ((rawKey, rawValue) in this) {
        if (!rawKey.startsWith(prefix)) continue

        val key = rawKey.removePrefix(prefix)
        val value = rawValue.trim()
        if (value.isEmpty()) continue

        when {
            key.endsWith(ConfigConstants.NAME_SUFFIX) -> {
                val enumQualifiedName = key.removeSuffix(ConfigConstants.NAME_SUFFIX)
                if (enumQualifiedName.isEmpty()) continue
                mutableConfigurations.getOrPut(enumQualifiedName, ::ExternalEnumOverride).name = value
            }

            key.endsWith(ConfigConstants.VISIBILITY_SUFFIX) -> {
                val enumQualifiedName = key.removeSuffix(ConfigConstants.VISIBILITY_SUFFIX)
                if (enumQualifiedName.isEmpty()) continue
                mutableConfigurations.getOrPut(enumQualifiedName, ::ExternalEnumOverride).visibility =
                    value.uppercase()
            }

            key.endsWith(ConfigConstants.PACKAGE_SUFFIX) -> {
                val enumQualifiedName = key.removeSuffix(ConfigConstants.PACKAGE_SUFFIX)
                if (enumQualifiedName.isEmpty()) continue
                mutableConfigurations.getOrPut(enumQualifiedName, ::ExternalEnumOverride).packageName = value
            }
        }
    }

    return mutableConfigurations
}

private fun resolveExternalEnumConfigurations(
    enumSets: Set<String>,
    enumMaps: Set<String>,
    externalEnumOverrides: Map<String, ExternalEnumOverride>,
): Set<ExternalEnumConfiguration> {
    val finalConfigurations = linkedSetOf<ExternalEnumConfiguration>()

    enumSets.forEach { enumQualifiedName ->
        val overrides = externalEnumOverrides[enumQualifiedName]
        finalConfigurations.add(
            ExternalEnumConfiguration(
                enumQualifiedName = enumQualifiedName,
                mode = GenMode.SET,
                packageName = overrides?.packageName,
                name = overrides?.name,
                visibility = overrides?.visibility,
            )
        )
    }

    enumMaps.forEach { enumQualifiedName ->
        val overrides = externalEnumOverrides[enumQualifiedName]
        finalConfigurations.add(
            ExternalEnumConfiguration(
                enumQualifiedName = enumQualifiedName,
                mode = GenMode.MAP,
                packageName = overrides?.packageName,
                name = overrides?.name,
                visibility = overrides?.visibility,
            )
        )
    }

    return finalConfigurations
}

private class ExternalEnumOverride(
    var packageName: String? = null,
    var name: String? = null,
    var visibility: String? = null,
)
