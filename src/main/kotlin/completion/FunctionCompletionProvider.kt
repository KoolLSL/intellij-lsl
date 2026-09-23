package io.github.koollsl.lsl.completion

import KwdbData
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import io.github.koollsl.lsl.preprocessor.LslIncludesCollector
import io.github.koollsl.lsl.psi.LslFunction

class FunctionCompletionProvider(val addSemicolon: Boolean) : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val currentFile = parameters.originalFile
        val project = currentFile.project

        // 1. Fetch cached included files via LslIncludesCollector
        val includesCollector = LslIncludesCollector.getInstance(project)
        val includedFiles = includesCollector.getIncludedFiles(currentFile)

        // ---------------------------------------------------------------------
        // Tier 1 (Priority 3.0): SAME FILE FUNCTIONS
        // ---------------------------------------------------------------------
        val sameFileFunctions = PsiTreeUtil.getChildrenOfTypeAsList(currentFile, LslFunction::class.java)

        val sameFileElements = sameFileFunctions
            .distinctBy { it.name }
            .mapNotNull { function ->
                createFunctionLookupElement(function, tailText = null, priority = 3.0)
            }

        // ---------------------------------------------------------------------
        // Tier 2 (Priority 2.0): INCLUDED MODULE FUNCTIONS (.lslm)
        // ---------------------------------------------------------------------
        val includedFunctions = includedFiles.flatMap { includedFile ->
            PsiTreeUtil.getChildrenOfTypeAsList(includedFile, LslFunction::class.java)
        }

        val includedElements = includedFunctions
            .distinctBy { it.name }
            .mapNotNull { function ->
                val varFile = function.containingFile
                val tailText = if (varFile != null) " (${varFile.name})" else null
                createFunctionLookupElement(function, tailText = tailText, priority = 2.0)
            }

        // ---------------------------------------------------------------------
        // Tier 3 (Priority 1.0): KWDB SYSTEM FUNCTIONS
        // ---------------------------------------------------------------------
        val kwdbFunctions = KwdbData.getInstance(project).functions.values

        val kwdbElements = kwdbFunctions
            .distinctBy { it.name }
            .mapNotNull { function ->
                createFunctionLookupElement(function, tailText = " (KWDB)", priority = 1.0)
            }

        // Add all tiered lookup elements to result
        result.addAllElements(sameFileElements)
        result.addAllElements(includedElements)
        result.addAllElements(kwdbElements)
    }

    private fun createFunctionLookupElement(
        function: LslFunction,
        tailText: String?,
        priority: Double
    ): LookupElement? {
        val funcName = function.name ?: return null

        val builder = LookupElementBuilder.create(funcName)
            .withRenderer(Renderer(function, tailText))
            .withInsertHandler { context, _ ->
                val templateManager = TemplateManager.getInstance(context.project)
                val template = templateManager.createTemplate(funcName, "lsl")

                template.addTextSegment("(")
                function.arguments.forEachIndexed { index, lslArgument ->
                    val argName = lslArgument.name ?: "arg$index"
                    if (index != 0) template.addTextSegment(", ")
                    template.addVariable(argName, ConstantNode(argName), true)
                }
                template.addEndVariable()

                if (addSemicolon) {
                    template.addTextSegment(");")
                } else {
                    template.addTextSegment(")")
                }

                templateManager.startTemplate(context.editor, template)
            }

        return PrioritizedLookupElement.withPriority(builder, priority)
    }

    class Renderer(
        private val function: LslFunction,
        private val originTailText: String?
    ) : LookupElementRenderer<LookupElement>() {

        override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
            presentation.icon = function.getIcon(false)
            presentation.itemText = function.name
            presentation.isItemTextBold = true
            presentation.typeText = function.lslType.toString()

            // Construct parameter list representation: funcName(integer a, string b)
            val paramsText = "(${function.arguments.joinToString { "${it.lslType} ${it.name}" }})"

            // Append source file / KWDB tag if present
            val fullTailText = if (originTailText != null) "$paramsText$originTailText" else paramsText

            presentation.setTailText(fullTailText, true)
        }
    }
}