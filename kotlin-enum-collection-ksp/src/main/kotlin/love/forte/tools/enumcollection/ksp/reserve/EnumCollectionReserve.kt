package love.forte.tools.enumcollection.ksp.reserve

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSFile
import love.forte.codegentle.kotlin.KotlinFile
import love.forte.codegentle.kotlin.ksp.writeTo
import love.forte.codegentle.kotlin.spec.KotlinFunctionSpec
import love.forte.codegentle.kotlin.spec.KotlinPropertySpec
import love.forte.codegentle.kotlin.spec.KotlinTypeSpec
import love.forte.tools.enumcollection.ksp.EnumDetail
import love.forte.tools.enumcollection.ksp.configuration.InheritanceMode
import love.forte.tools.enumcollection.ksp.configuration.KecConfiguration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.FULL)
    .withLocale(Locale.ROOT)
    .withZone(ZoneOffset.UTC)

/**
 * A resolved code-generation task for a single enum collection type.
 *
 * @author Forte Scarlet
 */
internal abstract class EnumCollectionReserve {
    abstract val sources: Set<KSFile>

    abstract val targetName: String

    abstract val visibility: String

    /**
     * Package where generated declarations should be emitted.
     */
    abstract val generatedPackageName: String

    abstract val enumDetail: EnumDetail

    /**
     * The qualified name of the optional API interface to inherit.
     *
     * When [KecConfiguration.inheritanceMode] is `AUTO`, we will check the existence of this type on classpath.
     */
    protected abstract val apiInheritanceTypeQualifiedName: String

    fun generate(resolver: Resolver, environment: SymbolProcessorEnvironment, configuration: KecConfiguration) {
        val codegen = environment.codeGenerator

        val apiInterfaceAvailable = resolver.getClassDeclarationByName(apiInheritanceTypeQualifiedName) != null
        val fileSpec = generateFileSpec(apiInterfaceAvailable, configuration.inheritanceMode)

        val kotlinFile = KotlinFile(
            packageNamePaths = generatedPackageName,
            types = fileSpec.types,
        ) {
            name(targetName)
            val time = DATETIME_FORMATTER.format(Instant.now())
            addFileComment("Auto-Generated at $time (UTC). Do not modify!")
            addFunctions(fileSpec.functions)
            addProperties(fileSpec.properties)
        }

        kotlinFile.writeTo(
            codeGenerator = codegen,
            aggregating = sources.isEmpty(),
            originatingKSFiles = sources,
        )
    }

    protected abstract fun generateFileSpec(inheritanceAvailable: Boolean, inheritanceMode: InheritanceMode): FileSpec

}

/**
 * Represents all top-level declarations to be emitted into the generated Kotlin file.
 */
internal data class FileSpec(
    val types: List<KotlinTypeSpec>,
    val functions: List<KotlinFunctionSpec> = emptyList(),
    val properties: List<KotlinPropertySpec> = emptyList(),
)
