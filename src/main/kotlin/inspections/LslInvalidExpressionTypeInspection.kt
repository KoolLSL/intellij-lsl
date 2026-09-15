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
import io.github.koollsl.lsl.parser.LslTypes
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.*

class LslInvalidExpressionTypeInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Invalid expression type"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = "Invalid expression type"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and preprocessor engine ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {

            // 1. Local Variable Initializations
            override fun visitStatementVariable(variable: LslStatementVariable) {
                if (preprocessorEngine.isDisabledText(file, variable.textRange)) return

                val declaredType = variable.lslType
                val initializer = variable.expression ?: return

                if (declaredType != LslPrimitiveType.INVALID) {
                    val actualType = initializer.lslType ?: LslPrimitiveType.INVALID
                    if (actualType != LslPrimitiveType.INVALID && declaredType.operationTo(actualType, LslTypes.ASSIGN) == LslPrimitiveType.INVALID) {
                        holder.registerProblem(
                            initializer,
                            "Type mismatch (expected %s, got %s)".format(declaredType, actualType),
                            ProblemHighlightType.GENERIC_ERROR,
                            TextRange(0, initializer.textLength),
                            TypeCastFix(initializer, declaredType)
                        )
                    }
                }
            }

            // 2. Global Variable Initializations
            override fun visitGlobalVariable(variable: LslGlobalVariable) {
                if (preprocessorEngine.isDisabledText(file, variable.textRange)) return

                val declaredType = variable.lslType
                val initializer = variable.expression ?: return

                if (declaredType != LslPrimitiveType.INVALID) {
                    val actualType = initializer.lslType ?: LslPrimitiveType.INVALID
                    if (actualType != LslPrimitiveType.INVALID && declaredType.operationTo(actualType, LslTypes.ASSIGN) == LslPrimitiveType.INVALID) {
                        holder.registerProblem(
                            initializer,
                            "Type mismatch (expected %s, got %s)".format(declaredType, actualType),
                            ProblemHighlightType.GENERIC_ERROR,
                            TextRange(0, initializer.textLength),
                            TypeCastFix(initializer, declaredType)
                        )
                    }
                }
            }

            // 3. Binary Expressions
            override fun visitExpressionBinary(expression: LslExpressionBinary) {
                if (preprocessorEngine.isDisabledText(file, expression.textRange)) return

                val typeLeft = expression.expressionLeft?.lslType ?: LslPrimitiveType.INVALID
                val typeRight = expression.expressionRight?.lslType ?: LslPrimitiveType.INVALID

                if (typeLeft != LslPrimitiveType.INVALID && typeRight != LslPrimitiveType.INVALID &&
                    typeLeft.operationTo(typeRight, expression.operator) == LslPrimitiveType.INVALID
                ) {
                    val expressionRight = expression.expressionRight
                    val fixes = listOfNotNull(
                        expressionRight?.let { TypeCastFix(it, typeLeft) }
                    ).toTypedArray()

                    holder.registerProblem(
                        expression,
                        "Type mismatch (expected %s, got %s)".format(typeLeft, typeRight),
                        ProblemHighlightType.GENERIC_ERROR,
                        TextRange(0, expression.textLength),
                        *fixes
                    )
                }
            }

            // 4. Vector Components
            override fun visitExpressionVector(expression: LslExpressionVector) {
                if (preprocessorEngine.isDisabledText(file, expression.textRange)) return

                expression.expressions.forEach { component ->
                    checkComponentType(component, holder)
                }
            }

            // 5. Rotation/Quaternion Components
            override fun visitExpressionQuaternion(expression: LslExpressionQuaternion) {
                if (preprocessorEngine.isDisabledText(file, expression.textRange)) return

                expression.expressions.forEach { component ->
                    checkComponentType(component, holder)
                }
            }

            // 6. Assignments in Conditions (if, while, do-while)
            override fun visitStatementIf(statement: LslStatementIf) {
                if (preprocessorEngine.isDisabledText(file, statement.textRange)) return
                checkConditionForAssignment(statement.condition, holder)
            }

            override fun visitStatementWhile(statement: LslStatementWhile) {
                if (preprocessorEngine.isDisabledText(file, statement.textRange)) return
                checkConditionForAssignment(statement.condition, holder)
            }

            override fun visitStatementDo(statement: LslStatementDo) {
                if (preprocessorEngine.isDisabledText(file, statement.textRange)) return
                checkConditionForAssignment(statement.condition, holder)
            }
        }
    }

    private fun checkComponentType(component: LslExpression, holder: ProblemsHolder) {
        val expressionType = component.lslType
        if (expressionType != LslPrimitiveType.INVALID &&
            LslPrimitiveType.FLOAT.operationTo(expressionType, LslTypes.ASSIGN) == LslPrimitiveType.INVALID
        ) {
            holder.registerProblem(
                component,
                "Type mismatch (expected float, got %s)".format(expressionType),
                ProblemHighlightType.GENERIC_ERROR,
                TextRange(0, component.textLength),
                TypeCastFix(component, LslPrimitiveType.FLOAT)
            )
        }
    }

    private fun checkConditionForAssignment(condition: PsiElement?, holder: ProblemsHolder) {
        if (condition == null) return

        val queue = ArrayDeque<PsiElement>()
        queue.add(condition)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            val isAssignment = when (current) {
                is LslExpressionBinary -> current.operator == LslTypes.ASSIGN
                is LslExpressionAssignment -> true
                else -> false
            }

            if (isAssignment) {
                holder.registerProblem(
                    current,
                    "Assignment in condition (did you mean '=='?)",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    TextRange(0, current.textLength)
                )
            }

            queue.addAll(current.children)
        }
    }

    class TypeCastFix(
        expression: LslExpression,
        private val type: LslPrimitiveType
    ) : LocalQuickFixOnPsiElement(expression) {

        override fun getFamilyName(): String = "Cast to $type"
        override fun getText(): String = familyName

        override fun invoke(
            project: Project,
            file: PsiFile,
            startElement: PsiElement,
            endElement: PsiElement
        ) {
            val expression = startElement as? LslExpression ?: return
            when (expression) {
                is LslExpressionBinary -> {
                    expression.replace(
                        LslElementFactory.createTypeCast(
                            project,
                            type,
                            LslElementFactory.createParentheses(project, expression)
                        )
                    )
                }
                else -> {
                    expression.replace(
                        LslElementFactory.createTypeCast(
                            project,
                            type,
                            expression
                        )
                    )
                }
            }
        }
    }
}