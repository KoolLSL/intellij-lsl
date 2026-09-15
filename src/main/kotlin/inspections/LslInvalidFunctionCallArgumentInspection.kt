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
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import io.github.koollsl.lsl.parser.LslTypes
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslExpressionFunctionCall
import io.github.koollsl.lsl.psi.LslFunction

class LslInvalidFunctionCallArgumentInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "Invalid function call argument"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = "Invalid function call argument"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and preprocessor service ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {

            override fun visitExpressionFunctionCall(expressionFunctionCall: LslExpressionFunctionCall) {
                // 2. Preprocessor check FIRST before resolving reference or evaluating arguments
                if (preprocessorEngine.isDisabledText(file, expressionFunctionCall.textRange)) return

                val targetFunction = expressionFunctionCall.reference?.resolve() as? LslFunction ?: return
                val arguments = targetFunction.arguments
                val expressions = expressionFunctionCall.expressions

                // 1. Check for argument type mismatches
                if (expressions.isNotEmpty()) {
                    (0 until minOf(expressions.size, arguments.size)).forEach { i ->
                        val argumentType = arguments[i].lslType
                        val expr = expressions[i]
                        val expressionType = expr.lslType

                        if (argumentType.operationTo(expressionType, LslTypes.ASSIGN) == LslPrimitiveType.INVALID) {
                            holder.registerProblem(
                                expr,
                                "Type mismatch (expected %s, got %s)".format(argumentType, expressionType),
                                ProblemHighlightType.GENERIC_ERROR,
                                LslInvalidExpressionTypeInspection.TypeCastFix(expr, argumentType)
                            )
                        }
                    }
                }

                // 2. Check for parameter count mismatches (too few)
                if (expressions.size < arguments.size) {
                    val targetRange = expressionFunctionCall.parenthesesRightEl?.textRangeInParent
                        ?: expressionFunctionCall.lastChild.textRangeInParent

                    holder.registerProblem(
                        expressionFunctionCall,
                        "Wrong arguments count (expected ${arguments.size}, got ${expressions.size})",
                        ProblemHighlightType.GENERIC_ERROR,
                        targetRange
                    )
                }
                // 3. Check too many arguments
                else if (expressions.size > arguments.size) {
                    val firstExtraExpression = if (arguments.isNotEmpty()) {
                        expressions[arguments.size]
                    } else {
                        expressions.first()
                    }

                    val firstExtraExpressionComma = expressionFunctionCall.node.getChildren(null)
                        .filter { it.elementType == LslTypes.COMMA }
                        .lastOrNull { it.psi.endOffset < firstExtraExpression.startOffset }
                        ?.psi

                    val lastExtraExpression = expressions.last()

                    val targetRange = TextRange(
                        firstExtraExpressionComma?.textRangeInParent?.startOffset
                            ?: firstExtraExpression.textRangeInParent.startOffset,
                        lastExtraExpression.textRangeInParent.endOffset
                    )

                    holder.registerProblem(
                        expressionFunctionCall,
                        "Wrong arguments count (expected ${arguments.size}, got ${expressions.size})",
                        ProblemHighlightType.GENERIC_ERROR,
                        targetRange,
                        RemoveExtraArgumentsFix(
                            firstExtraExpressionComma ?: firstExtraExpression,
                            lastExtraExpression
                        )
                    )
                }
            }
        }
    }

    class RemoveExtraArgumentsFix(startElement: PsiElement, endElement: PsiElement) :
        LocalQuickFixOnPsiElement(startElement, endElement) {

        override fun getFamilyName(): String = "Remove extra arguments"
        override fun getText(): String = familyName

        override fun invoke(
            project: Project,
            file: PsiFile,
            startElement: PsiElement,
            endElement: PsiElement
        ) {
            startElement.parent?.deleteChildRange(startElement, endElement)
        }
    }
}