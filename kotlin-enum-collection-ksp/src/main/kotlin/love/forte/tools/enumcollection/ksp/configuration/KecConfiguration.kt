package love.forte.tools.enumcollection.ksp.configuration

/**
 *
 * @author Forte Scarlet
 */
internal data class KecConfiguration(
    val enums: Set<String>,
    val inheritanceMode: InheritanceMode,
) {
    companion object {
        val defaultInheritanceMode = InheritanceMode.NEVER
    }

}