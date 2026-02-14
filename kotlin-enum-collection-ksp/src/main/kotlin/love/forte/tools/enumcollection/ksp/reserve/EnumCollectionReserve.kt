package love.forte.tools.enumcollection.ksp.reserve

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSFile
import love.forte.codegentle.kotlin.KotlinFile
import love.forte.codegentle.kotlin.spec.KotlinTypeSpec
import love.forte.tools.enumcollection.ksp.EnumDetail
import love.forte.tools.enumcollection.ksp.configuration.InheritanceMode
import love.forte.tools.enumcollection.ksp.configuration.KecConfiguration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DATETIME_FORMATTER = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.FULL)
    .withZone(ZoneOffset.UTC)

/**
 *
 * @author Forte Scarlet
 */
internal abstract class EnumCollectionReserve {
    abstract val sources: Set<KSFile>

    abstract val targetName: String

    abstract val visibility: String

    abstract val enumDetail: EnumDetail

    fun generate(resolver: Resolver, environment: SymbolProcessorEnvironment, configuration: KecConfiguration) {
        val codegen = environment.codeGenerator

        // TODO check EnumSet, EnumMap inheritance available
        val inheritanceAvailable = false // TODO

        val type = generateType(inheritanceAvailable, configuration.inheritanceMode)

        KotlinFile(
            packageNamePaths = enumDetail.packageName,
            type = type,
        ) {
            name(type.name)
            val time = DATETIME_FORMATTER.format(Instant.now())
            addFileComment("Auto-Generated at $time. Do not modify!")


            // TODO
        }

    }

    protected abstract fun generateType(inheritanceAvailable: Boolean, inheritanceMode: InheritanceMode): KotlinTypeSpec

}