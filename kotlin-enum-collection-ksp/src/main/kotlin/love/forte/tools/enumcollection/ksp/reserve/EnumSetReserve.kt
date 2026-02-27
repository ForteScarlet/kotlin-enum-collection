package love.forte.tools.enumcollection.ksp.reserve

import com.google.devtools.ksp.symbol.KSFile
import love.forte.codegentle.common.naming.ClassName
import love.forte.codegentle.common.naming.canonicalName
import love.forte.codegentle.common.naming.simpleNames
import love.forte.tools.enumcollection.ksp.EnumDetail
import love.forte.tools.enumcollection.ksp.configuration.InheritanceMode

/**
 * Reserve for generating enum-specialized set implementations.
 *
 * The generated Kotlin file contains:
 * - An immutable interface: [targetName]
 * - A mutable interface: `Mutable[targetName]`
 * - Top-level util + factory functions
 * - Private implementation classes
 */
internal class EnumSetReserve(
    override val sources: Set<KSFile>,
    override val targetName: String,
    override val visibility: String,
    override val generatedPackageName: String,
    override val enumDetail: EnumDetail,
) : EnumCollectionReserve() {

    override val apiInheritanceTypeQualifiedName: String = API_ENUM_SET.canonicalName

    override fun generateFileSpec(
        inheritanceAvailable: Boolean,
        inheritanceMode: InheritanceMode,
    ): FileSpec {
        val inheritApi = shouldInheritApiType(inheritanceMode, inheritanceAvailable)
        val enumType = ClassName(enumDetail.qualifiedName)
        val enumRef = enumType.simpleNames.joinToString(".")
        val entrySize = enumDetail.elements.size

        return when {
            entrySize <= Int.SIZE_BITS -> EnumSetBitsetFileGenerator.generateFileSpec(
                enumDetail = enumDetail,
                targetName = targetName,
                visibility = visibility,
                generatedPackageName = generatedPackageName,
                enumType = enumType,
                enumRef = enumRef,
                enumSize = entrySize,
                inheritApi = inheritApi,
                bitsetKind = EnumSetBitsetFileGenerator.BitsetKind.I32,
            )

            entrySize <= Long.SIZE_BITS -> EnumSetBitsetFileGenerator.generateFileSpec(
                enumDetail = enumDetail,
                targetName = targetName,
                visibility = visibility,
                generatedPackageName = generatedPackageName,
                enumType = enumType,
                enumRef = enumRef,
                enumSize = entrySize,
                inheritApi = inheritApi,
                bitsetKind = EnumSetBitsetFileGenerator.BitsetKind.I64,
            )

            else -> EnumSetWordsetFileGenerator.generateFileSpec(
                enumDetail = enumDetail,
                targetName = targetName,
                visibility = visibility,
                generatedPackageName = generatedPackageName,
                enumType = enumType,
                enumRef = enumRef,
                enumSize = entrySize,
                inheritApi = inheritApi,
            )
        }
    }
}
