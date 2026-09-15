package io.github.koollsl.lsl.references

import com.intellij.openapi.components.service
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import io.github.koollsl.lsl.preprocessor.LslIncludesCollector
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslFile
import io.github.koollsl.lsl.psi.LslFunction
import io.github.koollsl.lsl.psi.LslGlobalVariable

class LslFileSymbols(
    val globalVariables: List<LslGlobalVariable>,
    val functions: List<LslFunction>,
    val includedFiles: List<LslFile>
)

object LslFileSymbolCache {
    fun getSymbols(file: LslFile): LslFileSymbols {
        // Cache symbols per file; invalidates only on PSI modification count changes
        return CachedValuesManager.getCachedValue(file) {
            val engine = file.project.service<LslPreprocessorEngine>()

            // 1. Traverse all global variables and filter disabled ones
            val globals = PsiTreeUtil.findChildrenOfType(file, LslGlobalVariable::class.java)
                .filter { !engine.isElementDisabled(it) }

            // 2. Traverse all functions across deep AST branches (#ifdef) and filter disabled ones
            val functions = PsiTreeUtil.findChildrenOfType(file, LslFunction::class.java)
                .filter { !engine.isElementDisabled(it) }

            // Retrieve preprocessor included files
            val rawIncludes = LslIncludesCollector.getInstance(file.project).getIncludedFiles(file)
            val includes = rawIncludes.filterIsInstance<LslFile>()

            CachedValueProvider.Result.create(
                LslFileSymbols(globals, functions, includes),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }
    }
}