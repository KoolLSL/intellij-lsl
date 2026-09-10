package io.github.koollsl.lsl.inspections

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
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.*

class LslUnusedVariableInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Unused variable"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and preprocessor service ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {
            override fun visitGlobalVariable(variable: LslGlobalVariable) {
                // 2. Preprocessor check FIRST before evaluating global variables
                if (preprocessorEngine.isDisabledText(file, variable.textRange)) return
                checkUnusedVariable(variable)
            }

            override fun visitStatementVariable(variable: LslStatementVariable) {
                // Preprocessor check FIRST before evaluating statement variables
                if (preprocessorEngine.isDisabledText(file, variable.textRange)) return
                checkUnusedVariable(variable)
            }

            private fun checkUnusedVariable(variable: LslVariable) {
                // 3. Guard check: process non-empty variables
                if (variable.textRange.isEmpty) return

                // 4. Determine appropriate LocalSearchScope based on variable scope
                val searchScope = when (variable) {
                    is LslGlobalVariable -> LocalSearchScope(file)
                    is LslStatementVariable -> {
                        val parentScope = variable.parents(false)
                            .firstOrNull { it is LslFunction || it is LslEvent } ?: file
                        LocalSearchScope(parentScope)
                    }
                    else -> return
                }

                // 5. Flag if no references exist within the scope
                if (ReferencesSearch.search(variable, searchScope).findFirst() == null) {
                    val variableName = variable.name ?: variable.identifyingElement?.text ?: "variable"

                    holder.registerProblem(
                        variable,
                        "Unused variable '$variableName'",
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        variable.identifyingElement?.textRangeInParent,
                        RemoveUnusedVariableFix(variable)
                    )
                }
            }
        }
    }

    class RemoveUnusedVariableFix(variable: LslVariable) : LocalQuickFixOnPsiElement(variable) {
        override fun getFamilyName(): String = "Remove unused variable"

        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            startElement.delete()
        }
    }
}