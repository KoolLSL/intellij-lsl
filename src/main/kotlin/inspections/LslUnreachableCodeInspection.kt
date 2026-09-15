package io.github.koollsl.lsl.inspections

import LslLanguage
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
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parents
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.*

private val FINAL_FUNCTIONS = setOf("llDie", "llResetScript")

class LslUnreachableCodeInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Unreachable code"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and preprocessor service ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {
            override fun visitStatementBlock(statementBlock: LslStatementBlock) {
                // 2. Preprocessor check FIRST before evaluating statement blocks
                if (preprocessorEngine.isDisabledText(file, statementBlock.textRange)) return

                // 3. Guard check: process non-empty statement blocks
                if (statementBlock.textRange.isEmpty) return

                // 4. Identify contiguous ranges of unreachable code in the block
                val unreachableCodeRanges = findUnreachableCodeRanges(statementBlock)

                unreachableCodeRanges.forEach { range ->
                    val first = range.first()
                    val last = range.last()

                    holder.registerProblem(
                        statementBlock,
                        "Unreachable code",
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        TextRange(first.startOffsetInParent, last.textRangeInParent.endOffset),
                        RemoveUnreachableCodeFix(first, last)
                    )
                }
            }
        }
    }

    private fun findUnreachableCodeRanges(block: LslStatementBlock): List<Array<PsiElement>> {
        var isReachable = true
        val currentUnreachableBlock = ArrayList<PsiElement>()
        val result = ArrayList<Array<PsiElement>>()

        val parentScope = block.parents(false)
            .firstOrNull { it is LslFunction || it is LslEvent } ?: return emptyList()

        val searchScope = LocalSearchScope(parentScope)

        block.children.forEach { element ->
            // If code was unreachable, check if a targeted label restores reachability
            if (!isReachable && element is LslStatementLabel && ReferencesSearch.search(element, searchScope).findFirst() != null) {
                isReachable = true

                if (currentUnreachableBlock.isNotEmpty()) {
                    result.add(currentUnreachableBlock.toTypedArray())
                    currentUnreachableBlock.clear()
                }
            }

            if (!isReachable) {
                currentUnreachableBlock.add(element)
            }

            // Determine if the current statement breaks subsequent code reachability
            isReachable = isReachable && when (element) {
                is LslStatementReturn -> false
                is LslStatementState -> false
                is LslStatementExpression -> !isFinalFunctionCall(element.children.singleOrNull() as? LslExpressionFunctionCall)
                else -> true
            }
        }

        if (currentUnreachableBlock.isNotEmpty()) {
            result.add(currentUnreachableBlock.toTypedArray())
            currentUnreachableBlock.clear()
        }

        return result
    }

    private fun isFinalFunctionCall(functionCall: LslExpressionFunctionCall?): Boolean {
        val functionName = functionCall?.functionName ?: return false
        return functionName in FINAL_FUNCTIONS
    }

    class RemoveUnreachableCodeFix(startElement: PsiElement, endElement: PsiElement) :
        LocalQuickFixOnPsiElement(startElement, endElement) {

        override fun getFamilyName(): String = "Remove unreachable code"

        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            startElement.parent.deleteChildRange(startElement, endElement)
        }
    }
}