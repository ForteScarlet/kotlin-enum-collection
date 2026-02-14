package love.forte.tools.enumcollection.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.validate
import love.forte.tools.enumcollection.ksp.configuration.KecConfiguration
import love.forte.tools.enumcollection.ksp.reserve.EnumCollectionReserve

/**
 *
 * @author Forte Scarlet
 */
internal class KecProcessor(
    private val environment: SymbolProcessorEnvironment,
    private val configuration: KecConfiguration
) : SymbolProcessor {
    private val logger = environment.logger
    private val codeGenerator = environment.codeGenerator

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val extensionEnumClassDeclarations = configuration.enums.map { extensionEnumClass ->
            resolver.getClassDeclarationByName(extensionEnumClass)
        }

        // resolve EnumSet, EnumMap
        val enumSetGrouped = resolver.getSymbolsWithAnnotation(AnnotationTypes.ENUM_SET_ANNOTATION_QUALIFY)
            .groupBy { it.validate() }
        val enumMapGrouped = resolver.getSymbolsWithAnnotation(AnnotationTypes.ENUM_MAP_ANNOTATION_QUALIFY)
            .groupBy { it.validate() }

        val reserves = mutableListOf<EnumCollectionReserve>()
        enumSetGrouped[false]?.let { resolveEnumSets(reserves, it) }
        enumMapGrouped[false]?.let { resolveEnumMaps(reserves, it) }

        for (reserve in reserves) {
            reserve.generate(resolver, environment, configuration)
        }

     return (enumSetGrouped[true] ?: emptyList()) + (enumMapGrouped[true] ?: emptyList())
    }

    private fun resolveEnumSets(
        reserves: MutableCollection<in EnumCollectionReserve>,
        enumSetAnnotatedList: List<KSAnnotated>
    ) {
        // TODO
    }

    private fun resolveEnumMaps(
        reserves: MutableCollection<in EnumCollectionReserve>,
        enumMapAnnotatedList: List<KSAnnotated>
    ) {
       // TODO
    }
}