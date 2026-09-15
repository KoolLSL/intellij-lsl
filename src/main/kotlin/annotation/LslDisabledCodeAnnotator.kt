package io.github.koollsl.lsl.annotation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.syntax.LslColorKeys

class LslDisabledCodeAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {

        if (element is PsiFile || element is PsiWhiteSpace) return

        val engine = element.project.service<LslPreprocessorEngine>()

        if (element.textLength > 0 &&
            element.firstChild == null &&
            engine.isElementDisabled(element)
        ) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(LslColorKeys.DISABLED_CODE)
                .create()
        }
    }
}
