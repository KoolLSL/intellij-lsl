package io.github.koollsl.lsl.inspections

import LslLanguage
import LslPrimitiveType
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslExpressionTypeCast

class LslRedundantTypeCastInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Redundant type cast"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and preprocessor service ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {
            override fun visitPsiElement(element: PsiElement) {
                if (element !is LslExpressionTypeCast) return
                if (element.textRange.isEmpty) return

                // 2. Preprocessor check FIRST before evaluating type casts
                if (preprocessorEngine.isDisabledText(file, element.textRange)) return

                val targetType = element.lslType
                if (targetType == LslPrimitiveType.INVALID) return

                val innerExpression = element.expression ?: return
                val expressionType = innerExpression.lslType ?: LslPrimitiveType.INVALID

                // 3. Check if casting to the same type is redundant
                if (targetType == expressionType) {
                    val endOffset = element.parenthesesRightEl?.textRangeInParent?.endOffset
                        ?: innerExpression.textRangeInParent.startOffset

                    val highlightRange = TextRange(0, endOffset)

                    // 4. Register problem and attach quick fix for redundant type casts
                    holder.registerProblem(
                        element,
                        "Redundant type cast",
                        ProblemHighlightType.WEAK_WARNING,
                        highlightRange,
                        RemoveRedundantTypeCastFix(element)
                    )
                }
            }
        }
    }

    class RemoveRedundantTypeCastFix(typeCast: LslExpressionTypeCast) : LocalQuickFixOnPsiElement(typeCast) {
        override fun getFamilyName(): String = "Remove redundant type cast"

        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            val typeCast = startElement as? LslExpressionTypeCast ?: return
            val expression = typeCast.expression ?: return
            typeCast.replace(expression)
        }
    }
}