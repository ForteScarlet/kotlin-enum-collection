package love.forte.tools.enumcollection.annotations

/**
 * Marks an enum type to instruct the generator to create a corresponding EnumSet implementation.
 *
 * @param name The name of the generated type; when empty, the generator uses its default naming rule:
 * `${typeName}EnumSet`.
 * @param visibility The visibility of the generated type.
 * @author Forte Scarlet
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
public annotation class EnumSet(
    val name: String = "",
    val visibility: Visibility = Visibility.INTERNAL
)
