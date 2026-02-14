package love.forte.tools.enumcollection.ksp.configuration

internal object ConfigConstants {
    const val CONFIG_PREFIX = "love.forte.tools.enumcollection"

    const val ENUMS_KEY = "enums"
    const val INHERITANCE_MODE_KEY = "inheritanceMode"
}

internal fun Map<String, String>.resolveOptionsToConfiguration(): KecConfiguration {
    val enums = resolveOption(ConfigConstants.ENUMS_KEY)?.splitToSequence(',')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.toSet()

    val inheritanceMode = resolveOption(ConfigConstants.INHERITANCE_MODE_KEY)
        ?.let { InheritanceMode.valueOf(it) }

    return KecConfiguration(
        enums = enums ?: emptySet(),
        inheritanceMode = inheritanceMode ?: KecConfiguration.defaultInheritanceMode
    )
}

private fun Map<String, String>.resolveOption(key: String): String? {
    return get("${ConfigConstants.CONFIG_PREFIX}.$key")
}