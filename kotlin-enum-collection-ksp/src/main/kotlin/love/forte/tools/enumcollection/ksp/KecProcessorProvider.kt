package love.forte.tools.enumcollection.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import love.forte.tools.enumcollection.ksp.configuration.resolveOptionsToConfiguration

/**
 *
 * @author Forte Scarlet
 */
public class KecProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val configuration = environment.options.resolveOptionsToConfiguration()
        return KecProcessor(environment, configuration)
    }
}