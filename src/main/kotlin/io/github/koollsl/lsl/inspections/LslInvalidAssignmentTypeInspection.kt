package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.tree.IElementType
import io.github.koollsl.lsl.KwdbData
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.LslPrimitiveType
import io.github.koollsl.lsl.parser.LslTypes
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.*

class LslInvalidAssignmentTypeInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "Invalid assignment type"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = "Invalid assignment type"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and services ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()
        val kwdbData = KwdbData.getInstance(holder.project)

        return object : LslElementVisitor() {

            override fun visitExpressionAssignment(expression: LslExpressionAssignment) {
                // 2. Preprocessor check FIRST before executing assignment type logic
                if (preprocessorEngine.isDisabledText(file, expression.textRange)) return

                val lValue = expression.lValue
                val identifierText = lValue?.node?.text
                val isConstant = identifierText != null && kwdbData.constants.containsKey(identifierText)

                if (isConstant) {
                    holder.registerProblem(
                        lValue ?: expression,
                        "Cannot assign to a constant",
                        ProblemHighlightType.GENERIC_ERROR
                    )
                    return
                }

                checkAssignment(
                    variableType = lValue?.lslType,
                    expression = expression.expression,
                    operator = expression.operator,
                    holder = holder
                )
            }

            override fun visitGlobalVariable(variable: LslGlobalVariable) {
                // 2. Preprocessor check FIRST before executing assignment type logic
                if (preprocessorEngine.isDisabledText(file, variable.textRange)) return

                checkAssignment(
                    variableType = variable.lslType,
                    expression = variable.expression,
                    operator = LslTypes.ASSIGN,
                    holder = holder
                )
            }

            override fun visitStatementVariable(variable: LslStatementVariable) {
                // 2. Preprocessor check FIRST before executing assignment type logic
                if (preprocessorEngine.isDisabledText(file, variable.textRange)) return

                checkAssignment(
                    variableType = variable.lslType,
                    expression = variable.expression,
                    operator = LslTypes.ASSIGN,
                    holder = holder
                )
            }
        }
    }

    private fun checkAssignment(
        variableType: LslPrimitiveType?,
        expression: LslExpression?,
        operator: IElementType?,
        holder: ProblemsHolder
    ) {
        if (variableType == null || expression == null) return

        val expressionType = expression.lslType ?: LslPrimitiveType.INVALID

        if (expressionType != LslPrimitiveType.INVALID &&
            variableType.operationTo(expressionType, operator) == LslPrimitiveType.INVALID
        ) {
            holder.registerProblem(
                expression,
                "Invalid assignment type (expected %s, got %s)".format(variableType, expressionType),
                ProblemHighlightType.GENERIC_ERROR
            )
        }
    }
}