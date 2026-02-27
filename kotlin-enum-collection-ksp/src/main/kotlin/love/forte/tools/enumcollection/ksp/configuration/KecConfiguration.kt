package love.forte.tools.enumcollection.ksp.configuration

/**
 * Parsed KSP configuration for enum collection generation.
 *
 * @property inheritanceMode Inheritance strategy for generated types.
 * @property basePackage Default package for config-driven external enum generation.
 * @property baseVisibility Default visibility for config-driven external enum generation.
 * @property externalEnumConfigurations Final normalized configuration items for external enums.
 * @author Forte Scarlet
 */
internal data class KecConfiguration(
    val inheritanceMode: InheritanceMode,
    val basePackage: String?,
    val baseVisibility: String,
    val externalEnumConfigurations: Set<ExternalEnumConfiguration>,
) {
    companion object {
        val DEFAULT_INHERITANCE_MODE = InheritanceMode.NEVER
        const val DEFAULT_BASE_VISIBILITY = "INTERNAL"
    }
}

/**
 * Generation mode for one external enum configuration item.
 */
internal enum class GenMode {
    SET,
    MAP,
}

/**
 * Final external-enum configuration item after merging:
 * - mode list (`enumSets` / `enumMaps`)
 * - base defaults (`basePackage` / `baseVisibility`)
 * - per-enum overrides (`${FQN}.xxx`)
 */
internal data class ExternalEnumConfiguration(
    val enumQualifiedName: String,
    val mode: GenMode,
    val packageName: String? = null,
    val name: String? = null,
    val visibility: String? = null,
)
