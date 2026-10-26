package io.github.koollsl.lsl.formatting

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.impl.source.tree.LeafPsiElement
import io.github.koollsl.lsl.parser.LslTypes

// TODO: rewrite it in some better way
open class LslBlock(
    val parent: LslBlock?,
    node: ASTNode, wrap: Wrap?, alignment: Alignment?, val indent_: Indent?,
    val spacingBuilder: SpacingBuilder,
    val codeStyleSettings: CommonCodeStyleSettings,
    val specialAlignment: Alignment? = null,
) : AbstractBlock(node, wrap, alignment) {
    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        val leftType = (child1 as? LslBlock)?.node?.elementType
        val rightType = (child2 as? LslBlock)?.node?.elementType
        if (child1 != null &&
            (leftType == LslTypes.PREPROCESSOR_DIRECTIVE || rightType == LslTypes.PREPROCESSOR_DIRECTIVE)
        ) {
            return Spacing.createSpacing(0, 0, 1, true, codeStyleSettings.KEEP_BLANK_LINES_IN_CODE)
        }
        if (leftType == LslTypes.BRACE_LEFT || rightType == LslTypes.BRACE_RIGHT) {
            return Spacing.createSpacing(
                0,
                0,
                1,
                true,
                minOf(1, codeStyleSettings.KEEP_BLANK_LINES_IN_CODE),
            )
        }
        return spacingBuilder.getSpacing(this, child1, child2)
    }

    override fun isLeaf(): Boolean = node.firstChildNode == null

    override fun buildChildren(): List<Block> {
        var specialChildAlignment: Alignment? = null
        var blocks = ArrayList<Block>()

        node.getChildren(null)
            .filterNot { isWhitespaceOrBlank(it) }
            .forEach {
                blocks.add(
                    when {
                        // TODO: write it in better way
                        it.elementType == LslTypes.GLOBAL_VARIABLE && codeStyleSettings.ALIGN_CONSECUTIVE_VARIABLE_DECLARATIONS -> {
                            if (hasBlankLineBefore(it)) {
                                specialChildAlignment = Alignment.createAlignment(true, Alignment.Anchor.RIGHT)
                            }

                            makeChildBlock(it, specialChildAlignment)
                        }

                        // skip comments between global variables
                        LslTypes.COMMENTS.contains(it.elementType) -> {
                            makeChildBlock(it, specialChildAlignment)
                        }

                        else -> {
                            specialChildAlignment = null

                            makeChildBlock(it, specialChildAlignment)
                        }
                    }
                )
            }

        return blocks.toList()
    }

    private fun isWhitespaceOrBlank(n: ASTNode?) =
        n == null || n.elementType == TokenType.WHITE_SPACE || n.text.isBlank()

    private fun hasBlankLineBefore(n: ASTNode?) =
        isWhitespaceOrBlank(n?.treePrev) && isWhitespaceOrBlank(n?.treePrev?.treePrev)

    protected open fun makeChildBlock(child: ASTNode, specialChildAlignment: Alignment?): LslBlock {
        val childIndent = when {
            child.elementType == LslTypes.PREPROCESSOR_DIRECTIVE &&
                    LslTypes.STATEMENTS.contains(node.elementType) -> Indent.getNormalIndent()

            child.elementType == LslTypes.PREPROCESSOR_DIRECTIVE -> Indent.getAbsoluteNoneIndent()

            child.psi is LeafPsiElement && child.psi !is PsiComment -> Indent.getNoneIndent()

            listOf(
                LslTypes.DEFAULT_STATE_DECLARATION,
                LslTypes.STATE_DECLARATION
            ).contains(node.elementType) -> Indent.getNormalIndent()

            !listOf(
                LslTypes.FUNCTION,
                LslTypes.EVENT,

                LslTypes.STATEMENT_BLOCK,
            ).contains(node.elementType) && child.elementType == LslTypes.STATEMENT_BLOCK -> Indent.getNoneIndent()

            node.elementType == LslTypes.STATEMENT_EXPRESSION -> Indent.getNoneIndent()

            isCommentBeforeElse(child) -> Indent.getNoneIndent()

            child.elementType == LslTypes.STATEMENT_ELSE -> Indent.getNoneIndent()

            LslTypes.STATEMENTS.contains(node.elementType) -> Indent.getNormalIndent()
            node.elementType == LslTypes.STATEMENT_ELSE -> Indent.getNormalIndent()

            node.elementType == LslTypes.ARGUMENTS -> Indent.getNormalIndent()

            LslTypes.EXPRESSIONS.contains(node.elementType) && child != node.firstChildNode -> Indent.getNormalIndent()

            else -> Indent.getNoneIndent()
        }

        val childAlignment = when {
            node.elementType == LslTypes.GLOBAL_VARIABLE && child.elementType == LslTypes.ASSIGN -> specialAlignment
            else -> null
        }

        return LslBlock(
            this,
            child,
            null,
            childAlignment,
            childIndent,
            spacingBuilder,
            codeStyleSettings,
            specialChildAlignment
        )
    }

    private fun isCommentBeforeElse(child: ASTNode): Boolean {
        if (node.elementType != LslTypes.STATEMENT_IF ||
            child.elementType != LslTypes.LINE_COMMENT && child.elementType != LslTypes.BLOCK_COMMENT
        ) {
            return false
        }

        var sibling = child.treeNext
        while (sibling != null) {
            if (isWhitespaceOrBlank(sibling) ||
                sibling.elementType == LslTypes.LINE_COMMENT ||
                sibling.elementType == LslTypes.BLOCK_COMMENT
            ) {
                sibling = sibling.treeNext
            } else {
                return sibling.elementType == LslTypes.STATEMENT_ELSE
            }
        }
        return false
    }

    override fun getIndent(): Indent? =
        indent_

    override fun getChildIndent(): Indent? =
        if (listOf(
                LslTypes.STATE_DECLARATION,
                LslTypes.DEFAULT_STATE_DECLARATION,

                LslTypes.FUNCTION,
                LslTypes.EVENT,

                LslTypes.ARGUMENTS,
                LslTypes.STATEMENT_BLOCK,
            ).contains(node.elementType)
        ) {
            Indent.getNormalIndent()
        } else {
            Indent.getNoneIndent()
        }
}