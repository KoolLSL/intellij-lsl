package io.github.koollsl.lsl.references

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.ResolveCache
import io.github.koollsl.lsl.KwdbData
import io.github.koollsl.lsl.preprocessor.LslIncludesCollector
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.*

class LslLValueReference(val element: LslLValue) :
    PsiReferenceBase<PsiElement>(element), PsiPolyVariantReference {

    private val engine: LslPreprocessorEngine =
        element.project.service<LslPreprocessorEngine>()

    override fun resolve(): PsiElement? =
        multiResolve(false).firstOrNull()?.element

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
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
        element.variableNameIdentifier?.textRangeInParent ?: TextRange.EMPTY_RANGE

    private fun resolveInner(): Array<ResolveResult> {
        if (engine.isElementDisabled(element)) {
            return arrayOf(PsiElementResolveResult(element))
        }

        val result = ArrayList<ResolveResult>()
        var node: PsiElement? = element

        while (node != null) {
            when (node) {
                // Check the variable is declared before usage, and is not in disabled code
                is LslStatementBlock ->
                    result.addAll(
                        node.children
                            .takeWhile { it.textOffset < element.textOffset }
                            .filterIsInstance<LslStatementVariable>()
                            .filter { it.name == element.variableName && !engine.isElementDisabled(it) }
                            .let { ArrayList(it).asReversed() }
                            .map { PsiElementResolveResult(it) }
                    )

                is LslEvent ->
                    result.addAll(
                        node.arguments
                            .filter { it.name == element.variableName }
                            .map { PsiElementResolveResult(it) }
                    )

                is LslFunction ->
                    result.addAll(
                        node.arguments
                            .filter { it.name == element.variableName }
                            .map { PsiElementResolveResult(it) }
                    )

                is LslFile -> {
                    val project = element.project

                    // 1. Local file globals
                    val localGlobals = node.children
                        .filterIsInstance<LslGlobalVariable>()
                        .filter { it.name == element.variableName && !engine.isElementDisabled(it) }
                        .let { ArrayList(it).asReversed() }
                        .map { PsiElementResolveResult(it) }

                    // 2. Included file globals
                    val includedFiles = LslIncludesCollector.getInstance(project).getIncludedFiles(node)
                    val includedGlobals = includedFiles.flatMap { file ->
                        (file as? LslFile)?.children
                            ?.filterIsInstance<LslGlobalVariable>()
                            ?.filter { it.name == element.variableName }
                            //?.filter { it.name == element.variableName&& !engine.isElementDisabled(it) }
                            ?.map { PsiElementResolveResult(it) }
                            ?: emptyList()
                    }

                    // 3. Built‑in constants
                    val builtinConstants = listOfNotNull(
                        KwdbData.getInstance(project).constants[element.variableName]
                    ).map { PsiElementResolveResult(it) }

                    return (result + localGlobals + includedGlobals + builtinConstants)
                        .toTypedArray()
                }
            }

            node = node.parent
        }

        return result.toTypedArray()
    }
}
