package io.github.koollsl.lsl.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

open class LslElementVisitor : PsiElementVisitor() {

    override fun visitElement(element: PsiElement) {

        when (element) {
            // Derived / specific interfaces MUST come before parent / base interfaces
            is LslStateCustom -> visitStateCustom(element)
            is LslState -> visitState(element)

            is LslEvent -> visitEvent(element)
            is LslFunction -> visitFunction(element)
            is LslGlobalVariable -> visitGlobalVariable(element)
            is LslStatementVariable -> visitStatementVariable(element)
            is LslExpressionAssignment -> visitExpressionAssignment(element)
            is LslExpressionBinary -> visitExpressionBinary(element)
            is LslExpressionFunctionCall -> visitExpressionFunctionCall(element)
            is LslExpressionVector -> visitExpressionVector(element)
            is LslExpressionQuaternion -> visitExpressionQuaternion(element)
            is LslExpressionTypeCast -> visitExpressionTypeCast(element)
            is LslLValue -> visitLValue(element)
            is LslStatementIf -> visitStatementIf(element)
            is LslStatementWhile -> visitStatementWhile(element)
            is LslStatementDo -> visitStatementDo(element)
            is LslStatementReturn -> visitStatementReturn(element)
            is LslStatementJump -> visitStatementJump(element)
            is LslStatementState -> visitStatementState(element)
            is LslStatementBlock -> visitStatementBlock(element)
            is LslStatementLabel -> visitStatementLabel(element)
            is LslArgument -> visitArgument(element)
            else -> visitPsiElement(element)
        }
    }

    open fun visitStateCustom(state: LslStateCustom) = visitState(state)
    open fun visitState(state: LslState) = visitPsiElement(state)
    open fun visitEvent(event: LslEvent) = visitPsiElement(event)
    open fun visitFunction(function: LslFunction) = visitPsiElement(function)
    open fun visitGlobalVariable(variable: LslGlobalVariable) = visitPsiElement(variable)
    open fun visitStatementVariable(variable: LslStatementVariable) = visitPsiElement(variable)
    open fun visitExpressionAssignment(expression: LslExpressionAssignment) = visitPsiElement(expression)
    open fun visitExpressionBinary(expression: LslExpressionBinary) = visitPsiElement(expression)
    open fun visitExpressionFunctionCall(expression: LslExpressionFunctionCall) = visitPsiElement(expression)
    open fun visitExpressionVector(expression: LslExpressionVector) = visitPsiElement(expression)
    open fun visitExpressionQuaternion(expression: LslExpressionQuaternion) = visitPsiElement(expression)
    open fun visitExpressionTypeCast(expression: LslExpressionTypeCast) = visitPsiElement(expression)
    open fun visitLValue(lValue: LslLValue) = visitPsiElement(lValue)
    open fun visitStatementIf(statement: LslStatementIf) = visitPsiElement(statement)
    open fun visitStatementWhile(statement: LslStatementWhile) = visitPsiElement(statement)
    open fun visitStatementDo(statement: LslStatementDo) = visitPsiElement(statement)
    open fun visitStatementReturn(statement: LslStatementReturn) = visitPsiElement(statement)
    open fun visitStatementJump(statement: LslStatementJump) = visitPsiElement(statement)
    open fun visitStatementState(statement: LslStatementState) = visitPsiElement(statement)
    open fun visitStatementBlock(block: LslStatementBlock) = visitPsiElement(block)
    open fun visitStatementLabel(label: LslStatementLabel) = visitPsiElement(label)
    open fun visitArgument(argument: LslArgument) = visitPsiElement(argument)

    open fun visitPsiElement(element: PsiElement) {}
}