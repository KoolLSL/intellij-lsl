package io.github.koollsl.lsl.psi

import LslPrimitiveType
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import io.github.koollsl.lsl.parser.LslTypes

class LslExpressionPrint(node: ASTNode) : ASTWrapperPsiElement(node), LslExpression {
    val expression: LslExpression?
        get() = findChildByType(LslTypes.EXPRESSIONS)

    override val lslType: LslPrimitiveType
        get() = LslPrimitiveType.INVALID
}