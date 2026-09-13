package io.github.koollsl.lsl.references

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.ResolveCache
import io.github.koollsl.lsl.KwdbData
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslExpressionFunctionCall
import io.github.koollsl.lsl.psi.LslFile

class LslExpressionFunctionCallReference(val element: LslExpressionFunctionCall) :
    PsiReferenceBase<PsiElement>(element), PsiPolyVariantReference {

    private val engine: LslPreprocessorEngine =
        element.project.service<LslPreprocessorEngine>()

    override fun resolve(): PsiElement? =
        multiResolve(false).firstOrNull()?.element

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        // Early return for disabled elements to bypass resolve cache overhead
        if (engine.isElementDisabled(element)) {
            return arrayOf(PsiElementResolveResult(element))
        }

        return ResolveCache.getInstance(element.project).resolveWithCaching(
            this,
            { referenceBase, _ -> referenceBase.resolveInner() },
            false,
            incompleteCode,
        )
    }

    override fun getRangeInElement(): TextRange =
        element.functionNameIdentifier?.textRangeInParent ?: TextRange.EMPTY_RANGE

    private fun resolveInner(): Array<ResolveResult> {
        // Double-check disablement inside cached resolve execution
        if (engine.isElementDisabled(element)) {
            return arrayOf(PsiElementResolveResult(element))
        }

        val functionName = element.functionName ?: return emptyArray()
        val project = element.project
        val containingFile = element.containingFile as? LslFile ?: return emptyArray()

        // 1. Local functions (from cached file symbols)
        val localFunctions = LslFileSymbolCache.getSymbols(containingFile)
            .functions
            .filter { it.name == functionName }

        // 2. Included files
        val includedFiles = LslFileSymbolCache.getSymbols(containingFile).includedFiles
        val includedFunctions = includedFiles.flatMap { file ->
            LslFileSymbolCache.getSymbols(file)
                .functions
                .filter { it.name == functionName }
        }

        // 3. Built‑in functions
        val builtinFunctions = listOfNotNull(
            KwdbData.getInstance(project).functions[functionName]
        )

        return (localFunctions + includedFunctions + builtinFunctions)
            .map { PsiElementResolveResult(it) }
            .toTypedArray()
    }
}