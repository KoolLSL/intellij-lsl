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
import com.intellij.psi.util.parentsOfType
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.parser.LslTypes
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslArgument
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslEvent
import io.github.koollsl.lsl.psi.LslFunction

class LslUnusedArgumentInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Unused argument"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and preprocessor service ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {
            override fun visitArgument(argument: LslArgument) {
                // 2. Preprocessor check FIRST before evaluating function arguments
                if (preprocessorEngine.isDisabledText(file, argument.textRange)) return

                // 3. Guard check: process non-empty arguments
                if (argument.textRange.isEmpty) return

                // 4. Event parameters are defined by the LSL language spec and cannot be removed
                if (argument.parentsOfType(LslEvent::class.java).any()) return

                // 5. Restrict search scope to the containing function body for performance
                val functionScope = argument.parentsOfType(LslFunction::class.java).firstOrNull() ?: return
                val searchScope = LocalSearchScope(functionScope)

                // 6. Flag if no references exist within the function scope
                if (ReferencesSearch.search(argument, searchScope).findFirst() == null) {
                    val argumentName = argument.name ?: argument.identifyingElement?.text ?: "argument"
                    holder.registerProblem(
                        argument,
                        "Unused argument '$argumentName'",
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        argument.identifyingElement?.textRangeInParent,
                        RemoveUnusedArgumentFix(argument)
                    )
                }
            }
        }
    }

    class RemoveUnusedArgumentFix(argument: LslArgument) : LocalQuickFixOnPsiElement(argument) {
        override fun getFamilyName(): String = "Remove unused argument"

        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            val parentNode = startElement.parent?.node ?: return
            val children = parentNode.getChildren(null).toList()
            val targetIndex = children.indexOf(startElement.node)
            if (targetIndex == -1) return

            // Look for a preceding comma, or trailing comma if it's the first argument
            val commaNode = children.subList(0, targetIndex).findLast { it.elementType == LslTypes.COMMA }
                ?: children.subList(targetIndex + 1, children.size).find { it.elementType == LslTypes.COMMA }

            listOfNotNull(commaNode?.psi, startElement).forEach { it.delete() }
        }
    }
}