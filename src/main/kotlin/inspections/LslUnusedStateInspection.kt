package io.github.koollsl.lsl.inspections

import LslLanguage
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslStateCustom

class LslUnusedStateInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Unused state"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and preprocessor service ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {
            override fun visitStateCustom(stateCustom: LslStateCustom) {
                // 2. Preprocessor check FIRST before evaluating custom states
                if (preprocessorEngine.isDisabledText(file, stateCustom.textRange)) return

                // 3. Guard check: process non-empty custom states
                if (stateCustom.textRange.isEmpty) return

                // 4. Scope reference search to the file since states are script-local
                val searchScope = LocalSearchScope(file)

                // 5. Flag if no references exist within the file scope
                if (ReferencesSearch.search(stateCustom, searchScope).findFirst() == null) {
                    val stateName = stateCustom.identifyingElement?.text ?: stateCustom.text

                    holder.registerProblem(
                        stateCustom,
                        "Unused state '$stateName'",
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        stateCustom.identifyingElement?.textRangeInParent,
                        RemoveUnusedStateFix(stateCustom)
                    )
                }
            }
        }
    }

    class RemoveUnusedStateFix(state: LslStateCustom) : LocalQuickFixOnPsiElement(state) {
        override fun getFamilyName(): String = "Remove unused state"

        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            startElement.delete()
        }
    }
}