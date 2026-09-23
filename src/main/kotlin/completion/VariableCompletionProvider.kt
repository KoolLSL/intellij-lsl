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
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import io.github.koollsl.lsl.preprocessor.LslIncludesCollector
import io.github.koollsl.lsl.psi.LslFunction
import io.github.koollsl.lsl.psi.LslVariable

class VariableCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val currentFile = parameters.originalFile
        val project = currentFile.project
        val position = parameters.position

        // 1. Fetch cached included files
        val includesCollector = LslIncludesCollector.getInstance(project)
        val includedFiles = includesCollector.getIncludedFiles(currentFile)

        // ---------------------------------------------------------------------
        // Tier 1 (Priority 3.0): SAME FILE (Local variables & Same-file Globals)
        // ---------------------------------------------------------------------
        val sameFileGlobals = PsiTreeUtil.getChildrenOfTypeAsList(currentFile, LslVariable::class.java)

        val enclosingFunctionOrEvent = PsiTreeUtil.getParentOfType(position, LslFunction::class.java)
        val sameFileLocals = if (enclosingFunctionOrEvent != null) {
            PsiTreeUtil.collectElements(enclosingFunctionOrEvent) { element ->
                element is LslVariable && element.textOffset < position.textOffset
            }.map { it as LslVariable }
        } else {
            emptyList()
        }

        val sameFileElements = (sameFileGlobals + sameFileLocals)
            .distinctBy { it.name }
            .mapNotNull { variable ->
                val varName = variable.name ?: return@mapNotNull null
                val builder = LookupElementBuilder.create(varName)
                    .withRenderer(Renderer(variable, null))

                PrioritizedLookupElement.withPriority(builder, 3.0)
            }

        // ---------------------------------------------------------------------
        // Tier 2 (Priority 2.0): INCLUDED MODULES (.lslm)
        // ---------------------------------------------------------------------
        val includedGlobalVariables = includedFiles.flatMap { includedFile ->
            PsiTreeUtil.getChildrenOfTypeAsList(includedFile, LslVariable::class.java)
        }

        val includedElements = includedGlobalVariables
            .distinctBy { it.name }
            .mapNotNull { variable ->
                val varName = variable.name ?: return@mapNotNull null
                val varFile = variable.containingFile
                val tailText = if (varFile != null) " (${varFile.name})" else null

                val builder = LookupElementBuilder.create(varName)
                    .withRenderer(Renderer(variable, tailText))

                PrioritizedLookupElement.withPriority(builder, 2.0)
            }

        // ---------------------------------------------------------------------
        // Tier 3 (Priority 1.0): KWDB SYSTEM CONSTANTS
        // ---------------------------------------------------------------------
        val kwdbConstants = KwdbData.getInstance(project).constants.values
        val kwdbElements = kwdbConstants
            .distinctBy { it.name }
            .mapNotNull { constant ->
                val name = constant.name ?: return@mapNotNull null
                val builder = LookupElementBuilder.create(name)
                    .withRenderer(Renderer(constant, " (KWDB)"))

                PrioritizedLookupElement.withPriority(builder, 1.0)
            }

        // Add all tiered lookup elements to result
        result.addAllElements(sameFileElements)
        result.addAllElements(includedElements)
        result.addAllElements(kwdbElements)
    }

    class Renderer(
        private val variable: LslVariable,
        private val tailText: String?
    ) : LookupElementRenderer<LookupElement>() {

        override fun renderElement(element: LookupElement, presentation: LookupElementPresentation) {
            presentation.icon = variable.getIcon(false)
            presentation.itemText = variable.name
            presentation.typeText = variable.lslType.toString()

            if (tailText != null) {
                presentation.setTailText(tailText, true)
            }
        }
    }
}