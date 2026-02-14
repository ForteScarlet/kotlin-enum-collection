package love.forte.tools.enumcollection.ksp.reserve

import com.google.devtools.ksp.symbol.KSFile
import love.forte.codegentle.common.naming.ClassName
import love.forte.codegentle.common.naming.canonicalName
import love.forte.codegentle.common.naming.simpleNames
import love.forte.tools.enumcollection.ksp.EnumDetail
import love.forte.tools.enumcollection.ksp.configuration.InheritanceMode

/**
 * Reserve for generating enum-specialized map implementations.
 *
 * The generated Kotlin file contains:
 * - An immutable interface: [targetName]
 * - A mutable interface: `Mutable[targetName]`
 * - Top-level util + factory functions
 * - Private implementation classes
 */
internal class EnumMapReserve(
    override val sources: Set<KSFile>,
    override val targetName: String,
    override val visibility: String,
    override val enumDetail: EnumDetail,
) : EnumCollectionReserve() {

    override val apiInheritanceTypeQualifiedName: String = API_ENUM_MAP.canonicalName

    override fun generateFileSpec(
        inheritanceAvailable: Boolean,
        inheritanceMode: InheritanceMode,
    ): FileSpec {
        val inheritApi = shouldInheritApiType(inheritanceMode, inheritanceAvailable)
        val enumType = ClassName(enumDetail.qualifiedName)
        val enumRef = enumType.simpleNames.joinToString(".")
        val entrySize = enumDetail.elements.size

        return when {
            entrySize <= Int.SIZE_BITS -> EnumMapBitsetFileGenerator.generateFileSpec(
                enumDetail = enumDetail,
                targetName = targetName,
                visibility = visibility,
                enumType = enumType,
                enumRef = enumRef,
                enumSize = entrySize,
                inheritApi = inheritApi,
                bitsetKind = EnumMapBitsetFileGenerator.BitsetKind.I32,
            )

            entrySize <= Long.SIZE_BITS -> EnumMapBitsetFileGenerator.generateFileSpec(
                enumDetail = enumDetail,
                targetName = targetName,
                visibility = visibility,
                enumType = enumType,
                enumRef = enumRef,
                enumSize = entrySize,
                inheritApi = inheritApi,
                bitsetKind = EnumMapBitsetFileGenerator.BitsetKind.I64,
            )

            else -> EnumMapWordsetFileGenerator.generateFileSpec(
                enumDetail = enumDetail,
                targetName = targetName,
                visibility = visibility,
                enumType = enumType,
                enumRef = enumRef,
                enumSize = entrySize,
                inheritApi = inheritApi,
            )
        }
    }
}
