package io.github.koollsl.lsl.inspections

import KwdbData
import LslLanguage
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslEvent
import io.github.koollsl.lsl.psi.LslNamedElement

class LslReservedIdentifierInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Reserved identifier"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file, preprocessor service, and keyword database ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()
        val kwdbData = KwdbData.getInstance(holder.project)
        val kwdbNames = kwdbData.constants.keys + kwdbData.functions.keys + kwdbData.events.keys

        return object : LslElementVisitor() {
            override fun visitPsiElement(element: PsiElement) {
                if (element !is LslNamedElement || element is LslEvent) return
                if (element.textRange.isEmpty) return

                // 2. Preprocessor check FIRST before evaluating reserved identifiers
                if (preprocessorEngine.isDisabledText(file, element.textRange)) return

                val name = element.name ?: return

                // 3. Check if the identifier matches a reserved keyword, constant, function, or event name
                if (kwdbNames.contains(name)) {
                    holder.registerProblem(
                        element,
                        "Reserved identifier",
                        ProblemHighlightType.GENERIC_ERROR,
                        element.identifyingElement?.textRangeInParent
                    )
                }
            }
        }
    }
}