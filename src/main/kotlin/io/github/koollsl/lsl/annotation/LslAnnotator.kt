package io.github.koollsl.lsl.annotation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.serviceOrNull
import com.intellij.psi.PsiElement
import io.github.koollsl.lsl.KwdbData
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslEvent
import io.github.koollsl.lsl.psi.LslExpressionFunctionCall
import io.github.koollsl.lsl.psi.LslLValue
import io.github.koollsl.lsl.syntax.LslColorKeys

class LslAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {

        // Avoid running preprocessor logic on tokens, whitespace, or irrelevant AST nodes
        // FAST EXIT 1: Filter elements BEFORE checking the preprocessor
        if (element !is LslExpressionFunctionCall &&
            element !is LslEvent &&
            element !is LslLValue
        ) {
            return
        }

        // Get project service once filtering passes
        val project = element.project
        val engine = project.serviceOrNull<LslPreprocessorEngine>() ?: return

        // FAST EXIT 3: Avoid runCatching; handle disabled check directly
        if (engine.isElementDisabled(element)) return

        val kwdbData = KwdbData.getInstance(project)

        // Main dispatch
        when (element) {
            is LslExpressionFunctionCall -> {
                val functionName = element.functionName ?: return
                if (kwdbData.functions.containsKey(functionName)) {
                    val resolved = element.reference?.resolve()
                    if (resolved == null || kwdbData.hasElement(resolved) || resolved == kwdbData.functions[functionName]) {
                        val target = element.functionNameIdentifier ?: element
                        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(target)
                            .textAttributes(LslColorKeys.BUILTIN_FUNCTION)
                            .create()
                    }
                }
            }

            is LslLValue -> {
                val variableName = element.variableName ?: return
                if (kwdbData.constants.containsKey(variableName)) {
                    val resolved = element.reference?.resolve()
                    if (resolved == null || kwdbData.hasElement(resolved) || resolved == kwdbData.constants[variableName]) {
                        val target = element.variableNameIdentifier ?: element
                        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(target)
                            .textAttributes(LslColorKeys.BUILTIN_CONSTANT)
                            .create()
                    }
                }
            }

            is LslEvent -> {
                val eventName = element.name ?: return
                if (kwdbData.events.containsKey(eventName)) {
                    val target = element.nameIdentifier ?: element
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(target)
                        .textAttributes(LslColorKeys.EVENT)
                        .create()
                }
            }
        }
    }
}