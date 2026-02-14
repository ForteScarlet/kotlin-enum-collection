package love.forte.tools.enumcollection.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate
import love.forte.tools.enumcollection.ksp.configuration.KecConfiguration
import love.forte.tools.enumcollection.ksp.reserve.EnumCollectionReserve
import love.forte.tools.enumcollection.ksp.reserve.EnumMapReserve
import love.forte.tools.enumcollection.ksp.reserve.EnumSetReserve

/**
 * KSP processor for generating enum-specialized set/map implementations.
 *
 * @author Forte Scarlet
 */
internal class KecProcessor(
    private val environment: SymbolProcessorEnvironment,
    private val configuration: KecConfiguration
) : SymbolProcessor {
    private val logger = environment.logger

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val reserves = mutableListOf<EnumCollectionReserve>()
        val generatedTypeNames = mutableSetOf<String>()

        fun addReserve(reserve: EnumCollectionReserve, origin: KSAnnotated?) {
            val fqName = "${reserve.enumDetail.packageName}.${reserve.targetName}"
            if (!generatedTypeNames.add(fqName)) {
                logger.error("Duplicate generated type: $fqName", origin)
                return
            }
            reserves.add(reserve)
        }

        // Config-driven enums (generate both Set/Map with defaults).
        for (enumClass in configuration.enums) {
            val declaration = resolver.getClassDeclarationByName(enumClass)
            if (declaration == null) {
                logger.warn("Configured enum type not found: $enumClass")
                continue
            }
            if (declaration.classKind != ClassKind.ENUM_CLASS) {
                logger.error("Configured type is not an enum class: $enumClass", declaration)
                continue
            }

            val enumDetail = declaration.toEnumDetail()
            val sources = declaration.containingFile?.let(::setOf) ?: emptySet()

            addReserve(
                EnumSetReserve(
                    sources = sources,
                    targetName = "${enumDetail.simpleName}EnumSet",
                    visibility = "INTERNAL",
                    enumDetail = enumDetail
                ),
                declaration
            )
            addReserve(
                EnumMapReserve(
                    sources = sources,
                    targetName = "${enumDetail.simpleName}EnumMap",
                    visibility = "INTERNAL",
                    enumDetail = enumDetail
                ),
                declaration
            )
        }

        val enumSetGrouped = resolver.getSymbolsWithAnnotation(AnnotationTypes.ENUM_SET_ANNOTATION_QUALIFY)
            .groupBy { it.validate() }
        val enumMapGrouped = resolver.getSymbolsWithAnnotation(AnnotationTypes.ENUM_MAP_ANNOTATION_QUALIFY)
            .groupBy { it.validate() }

        enumSetGrouped[true]?.let { resolveEnumSets(it, ::addReserve) }
        enumMapGrouped[true]?.let { resolveEnumMaps(it, ::addReserve) }

        for (reserve in reserves) {
            reserve.generate(resolver, environment, configuration)
        }

        val deferred = (enumSetGrouped[false] ?: emptyList()) + (enumMapGrouped[false] ?: emptyList())
        return deferred
    }

    private fun resolveEnumSets(
        enumSetAnnotatedList: List<KSAnnotated>,
        addReserve: (EnumCollectionReserve, KSAnnotated?) -> Unit
    ) {
        for (annotated in enumSetAnnotatedList) {
            val declaration = annotated as? KSClassDeclaration
            if (declaration == null) {
                logger.error("@EnumSet can only be applied to enum classes.", annotated)
                continue
            }
            if (declaration.classKind != ClassKind.ENUM_CLASS) {
                logger.error("@EnumSet can only be applied to enum classes.", declaration)
                continue
            }

            val annotation = declaration.findAnnotation(AnnotationTypes.ENUM_SET_ANNOTATION_QUALIFY)
            val enumDetail = declaration.toEnumDetail()
            val name = annotation?.stringArgument("name").orEmpty()
            val targetName = name.ifBlank { "${enumDetail.simpleName}EnumSet" }
            val visibility = annotation?.enumArgumentSimpleName("visibility")?.uppercase() ?: "INTERNAL"
            val sources = declaration.containingFile?.let(::setOf) ?: emptySet()

            addReserve(
                EnumSetReserve(
                    sources = sources,
                    targetName = targetName,
                    visibility = visibility,
                    enumDetail = enumDetail
                ),
                declaration
            )
        }
    }

    private fun resolveEnumMaps(
        enumMapAnnotatedList: List<KSAnnotated>,
        addReserve: (EnumCollectionReserve, KSAnnotated?) -> Unit
    ) {
        for (annotated in enumMapAnnotatedList) {
            val declaration = annotated as? KSClassDeclaration
            if (declaration == null) {
                logger.error("@EnumMap can only be applied to enum classes.", annotated)
                continue
            }
            if (declaration.classKind != ClassKind.ENUM_CLASS) {
                logger.error("@EnumMap can only be applied to enum classes.", declaration)
                continue
            }

            val annotation = declaration.findAnnotation(AnnotationTypes.ENUM_MAP_ANNOTATION_QUALIFY)
            val enumDetail = declaration.toEnumDetail()
            val name = annotation?.stringArgument("name").orEmpty()
            val targetName = name.ifBlank { "${enumDetail.simpleName}EnumMap" }
            val visibility = annotation?.enumArgumentSimpleName("visibility")?.uppercase() ?: "INTERNAL"
            val sources = declaration.containingFile?.let(::setOf) ?: emptySet()

            addReserve(
                EnumMapReserve(
                    sources = sources,
                    targetName = targetName,
                    visibility = visibility,
                    enumDetail = enumDetail
                ),
                declaration
            )
        }
    }

    private fun KSClassDeclaration.toEnumDetail(): EnumDetail {
        val entries = declarations.filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.ENUM_ENTRY }
            .map { it.simpleName.asString() }
            .toList()

        val pkg = packageName.asString()
        val simple = simpleName.asString()
        val qName = qualifiedName?.asString() ?: if (pkg.isBlank()) simple else "$pkg.$simple"

        return EnumDetail(
            packageName = pkg,
            qualifiedName = qName,
            simpleName = simple,
            elements = entries
        )
    }
}

private fun KSClassDeclaration.findAnnotation(qualifiedName: String): KSAnnotation? =
    annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName }

private fun KSAnnotation.stringArgument(name: String): String? =
    arguments.firstOrNull { it.name?.asString() == name }?.value as? String

private fun KSAnnotation.enumArgumentSimpleName(name: String): String? =
    arguments.firstOrNull { it.name?.asString() == name }?.value?.let(::enumValueSimpleName)

private fun enumValueSimpleName(value: Any?): String? = when (value) {
    is KSName -> value.asString().substringAfterLast('.')
    is KSClassDeclaration -> value.simpleName.asString()
    is KSType -> value.declaration.simpleName.asString()
    is Enum<*> -> value.name
    is String -> value.substringAfterLast('.')
    else -> value?.toString()?.substringAfterLast('.')
}
