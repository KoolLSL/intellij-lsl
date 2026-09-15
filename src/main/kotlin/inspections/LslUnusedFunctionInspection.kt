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
import com.intellij.psi.search.searches.ReferencesSearch
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslFunction
import io.github.koollsl.lsl.references.LslReferenceUtils.getLslIncludeScope

class LslUnusedFunctionInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Unused function"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and preprocessor service ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {
            override fun visitPsiElement(element: PsiElement) {
                // 2. Guard checks: process non-empty functions
                if (element !is LslFunction) return
                if (element.textRange.isEmpty) return

                // 3. Preprocessor check FIRST before evaluating function
                if (preprocessorEngine.isElementDisabled(element)) return

                // 4. Resolve include-aware scope (.lsl = local file, .lslm = project dependents)
                val searchScope = getLslIncludeScope(file)

                // 5. Short-circuit search for active references using .filtering
                val hasUsages = ReferencesSearch.search(element, searchScope)
                    .filtering { reference -> !preprocessorEngine.isElementDisabled(reference.element) }
                    .findFirst() != null

                // 6. Flag if no enabled references exist
                if (!hasUsages) {
                    val functionName = element.name ?: element.identifyingElement?.text ?: "function"
                    holder.registerProblem(
                        element,
                        "Unused function '$functionName'",
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        element.identifyingElement?.textRangeInParent,
                        RemoveUnusedFunctionFix(element)
                    )
                }
            }
        }
    }

    class RemoveUnusedFunctionFix(function: LslFunction) : LocalQuickFixOnPsiElement(function) {
        override fun getFamilyName(): String = "Remove unused function"

        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            startElement.delete()
        }
    }
}