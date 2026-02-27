package love.forte.tools.enumcollection.ksp.configuration

internal object ConfigConstants {
    const val CONFIG_PREFIX = "love.forte.tools.enumcollection"

    const val ENUMS_KEY = "enums"
    const val ENUM_SETS_KEY = "enumSets"
    const val ENUM_MAPS_KEY = "enumMaps"
    const val INHERITANCE_MODE_KEY = "inheritanceMode"
}

internal fun Map<String, String>.resolveOptionsToConfiguration(): KecConfiguration {
    val enumSets = resolveOption(ConfigConstants.ENUM_SETS_KEY)?.splitToSequence(',')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.toSet()
    val enumMaps = resolveOption(ConfigConstants.ENUM_MAPS_KEY)?.splitToSequence(',')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.toSet()

    val inheritanceMode = resolveOption(ConfigConstants.INHERITANCE_MODE_KEY)
        ?.let { InheritanceMode.valueOf(it.replace('-', '_').uppercase()) }

    return KecConfiguration(
        enumSets = enumSets ?: emptySet(),
        enumMaps = enumMaps ?: emptySet(),
        inheritanceMode = inheritanceMode ?: KecConfiguration.defaultInheritanceMode
    )
}

private fun Map<String, String>.resolveOption(key: String): String? {
    return get("${ConfigConstants.CONFIG_PREFIX}.$key")
}