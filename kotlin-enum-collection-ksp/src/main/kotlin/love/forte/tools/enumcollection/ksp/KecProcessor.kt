package love.forte.tools.enumcollection.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import love.forte.tools.enumcollection.ksp.configuration.ExternalEnumConfiguration
import love.forte.tools.enumcollection.ksp.configuration.GenMode
import love.forte.tools.enumcollection.ksp.configuration.KecConfiguration
import love.forte.tools.enumcollection.ksp.reserve.EnumCollectionReserve
import love.forte.tools.enumcollection.ksp.reserve.EnumMapReserve
import love.forte.tools.enumcollection.ksp.reserve.EnumSetReserve
import java.util.concurrent.ConcurrentSkipListSet

private data class ConfigDrivenGenerationTarget(
    val packageName: String,
    val targetName: String,
    val visibility: String,
)

/**
 * KSP processor for generating enum-specialized set/map implementations.
 *
 * @author Forte Scarlet
 */
internal class KecProcessor(
    private val environment: SymbolProcessorEnvironment,
    private val configuration: KecConfiguration
) : SymbolProcessor {
    private data class ResolvedFromConfigurationIdentity(val name: String, val mode: GenMode)

    private val logger = environment.logger
    private val resolvedFromConfiguration = ConcurrentSkipListSet<ResolvedFromConfigurationIdentity>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val reserves = mutableListOf<EnumCollectionReserve>()
        val generatedTypeNames = mutableSetOf<String>()
        val invalidates = mutableSetOf<KSAnnotated>()

        fun addReserve(reserve: EnumCollectionReserve, origin: KSAnnotated?) {
            val fqName = buildGeneratedTypeFqName(reserve.generatedPackageName, reserve.targetName)
            if (!generatedTypeNames.add(fqName)) {
                logger.warn("Duplicate generated type: $fqName", origin)
                return
            }
            reserves.add(reserve)
        }

        // Config-driven enumSets/enumMaps.
        fun addReserveFromConfiguration(externalConfiguration: ExternalEnumConfiguration) {
            val configIdentity = ResolvedFromConfigurationIdentity(externalConfiguration.enumQualifiedName, externalConfiguration.mode)
            if (configIdentity in resolvedFromConfiguration) {
                return
            }

            val declaration = resolver.getClassDeclarationByName(externalConfiguration.enumQualifiedName)
            if (declaration == null) {
                logger.warn("Configured enum type not found: ${externalConfiguration.enumQualifiedName}")
                return
            }
            if (declaration.classKind != ClassKind.ENUM_CLASS) {
                logger.error(
                    "Configured type is not an enum class: ${externalConfiguration.enumQualifiedName}",
                    declaration
                )
                return
            }

            if (!declaration.validate()) {
                invalidates.add(declaration)
                return
            }

            val enumDetail = declaration.toEnumDetail()
            val sources = declaration.containingFile?.let(::setOf) ?: emptySet()
            val generationTarget = resolveConfigDrivenTarget(externalConfiguration, enumDetail)

            when (externalConfiguration.mode) {
                GenMode.SET -> {
                    addReserve(
                        EnumSetReserve(
                            sources = sources,
                            targetName = generationTarget.targetName,
                            visibility = generationTarget.visibility,
                            generatedPackageName = generationTarget.packageName,
                            enumDetail = enumDetail
                        ),
                        declaration
                    )
                }

                GenMode.MAP -> {
                    addReserve(
                        EnumMapReserve(
                            sources = sources,
                            targetName = generationTarget.targetName,
                            visibility = generationTarget.visibility,
                            generatedPackageName = generationTarget.packageName,
                            enumDetail = enumDetail
                        ),
                        declaration
                    )
                }
            }

            resolvedFromConfiguration.add(configIdentity)
        }

        for (externalConfiguration in configuration.externalEnumConfigurations) {
            addReserveFromConfiguration(externalConfiguration)
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

        invalidates.addAll(enumSetGrouped[false] ?: emptyList())
        invalidates.addAll(enumMapGrouped[false] ?: emptyList())

        return invalidates.toList()
    }

    private fun resolveEnumSets(
        enumSetAnnotatedList: List<KSAnnotated>,
        addReserve: (EnumCollectionReserve, KSAnnotated?) -> Unit
    ) {
        for (annotated in enumSetAnnotatedList) {
            val declaration = annotated as? KSClassDeclaration
            if (declaration == null) {
                logger.error(
                    "@EnumSet can only be applied to enum classes, but [$annotated] can not be a KSClassDeclaration",
                    annotated
                )
                continue
            }
            if (declaration.classKind != ClassKind.ENUM_CLASS) {
                logger.error(
                    "@EnumSet can only be applied to enum classes, but [$annotated] is not an ENUM_CLASS, " +
                            "it's ${declaration.classKind}",
                    declaration
                )
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
                    generatedPackageName = enumDetail.packageName,
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
                logger.error(
                    "@EnumMap can only be applied to enum classes, but [$annotated] can not be a KSClassDeclaration",
                    annotated
                )
                continue
            }
            if (declaration.classKind != ClassKind.ENUM_CLASS) {
                logger.error(
                    "@EnumMap can only be applied to enum classes, but [$annotated] is not an ENUM_CLASS, " +
                            "it's ${declaration.classKind}",
                    declaration
                )
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
                    generatedPackageName = enumDetail.packageName,
                    enumDetail = enumDetail
                ),
                declaration
            )
        }
    }

    private fun resolveConfigDrivenTarget(
        externalConfiguration: ExternalEnumConfiguration,
        enumDetail: EnumDetail,
    ): ConfigDrivenGenerationTarget {
        val defaultName = when (externalConfiguration.mode) {
            GenMode.SET -> "${enumDetail.simpleName}EnumSet"
            GenMode.MAP -> "${enumDetail.simpleName}EnumMap"
        }

        val packageName = externalConfiguration.packageName
            ?: configuration.basePackage
            ?: enumDetail.packageName

        val targetName = externalConfiguration.name ?: defaultName

        val visibility = normalizeVisibility(
            rawVisibility = externalConfiguration.visibility ?: configuration.baseVisibility,
            fallback = KecConfiguration.DEFAULT_BASE_VISIBILITY,
            optionName = buildString {
                append("love.forte.tools.enumcollection.")
                if (externalConfiguration.visibility != null) {
                    append(externalConfiguration.enumQualifiedName)
                    append(".visibility")
                } else {
                    append("baseVisibility")
                }
            }
        )

        return ConfigDrivenGenerationTarget(
            packageName = packageName,
            targetName = targetName,
            visibility = visibility,
        )
    }

    private fun normalizeVisibility(rawVisibility: String?, fallback: String, optionName: String): String {
        val normalized = rawVisibility?.trim()?.takeIf { it.isNotEmpty() }?.uppercase() ?: fallback
        return when (normalized) {
            "PUBLIC",
            "INTERNAL",
            "PRIVATE",
            -> normalized

            else -> {
                logger.warn(
                    "Unsupported visibility value '$normalized' for option '$optionName'. " +
                            "Fallback to '$fallback'."
                )
                fallback
            }
        }
    }

    private fun buildGeneratedTypeFqName(packageName: String, targetName: String): String {
        return if (packageName.isBlank()) targetName else "$packageName.$targetName"
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
