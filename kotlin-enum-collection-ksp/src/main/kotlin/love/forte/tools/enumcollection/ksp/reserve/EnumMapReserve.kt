package love.forte.tools.enumcollection.ksp.reserve

import com.google.devtools.ksp.symbol.KSFile
import love.forte.codegentle.kotlin.spec.KotlinTypeSpec
import love.forte.tools.enumcollection.ksp.EnumDetail
import love.forte.tools.enumcollection.ksp.configuration.InheritanceMode

/**
 *
 * @author Forte Scarlet
 */
internal class EnumMapReserve(
    override val sources: Set<KSFile>,
    override val targetName: String,
    override val visibility: String,
    override val enumDetail: EnumDetail,
) : EnumCollectionReserve() {
    override fun generateType(
        inheritanceAvailable: Boolean,
        inheritanceMode: InheritanceMode
    ): KotlinTypeSpec {
        // TODO 根据 inheritanceMode 配置生成继承关系: 如果
        // TODO 根据元素数量选择基于 i32、i64、large 进行实现
        //  或者将 EnumMapReserve 再拆成三个基于 i32、i64、large 进行实现的类型也行

        // return KotlinSimpleTypeSpec(KotlinTypeSpec.Kind.CLASS, targetName) {
        //     when (visibility.uppercase()) {
        //         "PUBLIC" -> modifiers { public() }
        //         "PRIVATE" -> modifiers { private() }
        //         "INTERNAL" -> modifiers { internal() }
        //     }
        //
        //
        //
        //
        // }

        TODO()
    }
}