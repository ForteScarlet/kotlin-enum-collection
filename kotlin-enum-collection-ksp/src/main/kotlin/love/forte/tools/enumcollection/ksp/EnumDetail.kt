package love.forte.tools.enumcollection.ksp

/**
 * Details for a target enum type.
 *
 * @property packageName The package name where the enum is declared.
 * @property qualifiedName The fully-qualified name of the enum type.
 * @property simpleName The simple name of the enum type.
 * @property elements Enum entry simple names in declaration order.
 * @author Forte Scarlet
 */
internal class EnumDetail(
    val packageName: String,
    val qualifiedName: String,
    val simpleName: String,
    val elements: List<String>,
)
