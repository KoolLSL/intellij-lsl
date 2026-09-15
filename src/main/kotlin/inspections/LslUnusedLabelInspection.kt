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
import com.intellij.psi.util.parents
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslEvent
import io.github.koollsl.lsl.psi.LslFunction
import io.github.koollsl.lsl.psi.LslStatementLabel

class LslUnusedLabelInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Unused label"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and preprocessor service ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {
            override fun visitStatementLabel(statementLabel: LslStatementLabel) {
                // 2. Preprocessor check FIRST before evaluating labels
                if (preprocessorEngine.isDisabledText(file, statementLabel.textRange)) return

                // 3. Guard check: process non-empty labels
                if (statementLabel.textRange.isEmpty) return

                // 4. Scope label searches to the containing function or event body
                val parentScope = statementLabel.parents(false)
                    .firstOrNull { it is LslFunction || it is LslEvent } ?: file
                val searchScope = LocalSearchScope(parentScope)

                // 5. Flag if no references exist within the scope
                if (ReferencesSearch.search(statementLabel, searchScope).findFirst() == null) {
                    val labelName = statementLabel.name ?: statementLabel.identifyingElement?.text ?: "label"
                    holder.registerProblem(
                        statementLabel,
                        "Unused label '$labelName'",
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        statementLabel.identifyingElement?.textRangeInParent,
                        RemoveUnusedLabelFix(statementLabel)
                    )
                }
            }
        }
    }

    class RemoveUnusedLabelFix(label: LslStatementLabel) : LocalQuickFixOnPsiElement(label) {
        override fun getFamilyName(): String = "Remove unused label"

        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            startElement.delete()
        }
    }
}