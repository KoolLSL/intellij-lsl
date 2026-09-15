package io.github.koollsl.lsl.references

import KwdbData
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.ResolveCache
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

        val targetName = element.variableName ?: return ResolveResult.EMPTY_ARRAY
        val result = ArrayList<ResolveResult>(2)
        var node: PsiElement? = element

        while (node != null) {
            when (node) {
                // 1. Local block scope: collect variables declared BEFORE this element in the same block
                is LslStatementBlock -> {
                    val elementOffset = element.textOffset
                    var child = node.firstChild
                    val localVars = ArrayList<LslStatementVariable>()

                    while (child != null) {
                        if (child.textOffset >= elementOffset) break
                        if (child is LslStatementVariable && child.name == targetName && !engine.isElementDisabled(child)) {
                            localVars.add(child)
                        }
                        child = child.nextSibling
                    }

                    // Traverse backwards for standard shadowing semantics
                    for (i in localVars.size - 1 downTo 0) {
                        result.add(PsiElementResolveResult(localVars[i]))
                    }
                }

                // 2. Event parameter scope
                is LslEvent -> {
                    val args = node.arguments
                    for (i in args.indices) {
                        val arg = args[i]
                        if (arg.name == targetName) {
                            result.add(PsiElementResolveResult(arg))
                        }
                    }
                }

                // 3. Function parameter scope
                is LslFunction -> {
                    val args = node.arguments
                    for (i in args.indices) {
                        val arg = args[i]
                        if (arg.name == targetName) {
                            result.add(PsiElementResolveResult(arg))
                        }
                    }
                }

                // 4. File-level scope: uses the Cached Value symbol table
                is LslFile -> {
                    // Early exit if resolved locally higher up the PSI stack
                    if (result.isNotEmpty()) {
                        return result.toTypedArray()
                    }

                    val symbols = LslFileSymbolCache.getSymbols(node)

                    // Local file globals (bottom-to-top preference)
                    for (i in symbols.globalVariables.size - 1 downTo 0) {
                        val globalVar = symbols.globalVariables[i]
                        if (globalVar.name == targetName) {
                            result.add(PsiElementResolveResult(globalVar))
                        }
                    }

                    // Included file globals (evaluated if no local global match)
                    if (result.isEmpty()) {
                        outer@ for (incFile in symbols.includedFiles) {
                            val incSymbols = LslFileSymbolCache.getSymbols(incFile)
                            for (globalVar in incSymbols.globalVariables) {
                                if (globalVar.name == targetName) {
                                    result.add(PsiElementResolveResult(globalVar))
                                    break@outer
                                }
                            }
                        }
                    }

                    // Built-in constants (Zero-allocation lookup)
                    if (result.isEmpty()) {
                        val builtinConstant = KwdbData.getInstance(element.project).constants[targetName]
                        if (builtinConstant != null) {
                            result.add(PsiElementResolveResult(builtinConstant))
                        }
                    }

                    return if (result.isEmpty()) ResolveResult.EMPTY_ARRAY else result.toTypedArray()
                }
            }

            // Move up to parent node in PSI hierarchy
            node = node.parent
        }

        return if (result.isEmpty()) ResolveResult.EMPTY_ARRAY else result.toTypedArray()
    }
}