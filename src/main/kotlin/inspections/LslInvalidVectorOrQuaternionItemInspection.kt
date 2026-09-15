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
import io.github.koollsl.lsl.psi.LslLValue

private val VECTOR_COMPONENTS = setOf("x", "y", "z")
private val QUATERNION_COMPONENTS = setOf("x", "y", "z", "s")

class LslInvalidVectorOrQuaternionItemInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Invalid vector or quaternion item"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and preprocessor service ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {
            override fun visitLValue(lValue: LslLValue) {
                if (lValue.textRange.isEmpty) return

                // 2. Preprocessor check FIRST before evaluating the l-value components
                if (preprocessorEngine.isDisabledText(file, lValue.textRange)) return

                val item = lValue.item
                if (item.isNullOrBlank()) return

                // 3. Validate component name based on the variable's primitive type
                val isInvalid = when (lValue.variable?.lslType) {
                    LslPrimitiveType.VECTOR -> item !in VECTOR_COMPONENTS
                    LslPrimitiveType.QUATERNION -> item !in QUATERNION_COMPONENTS
                    else -> false
                }

                // 4. Register problem and attach quick fix for invalid components
                if (isInvalid) {
                    val dot = lValue.dot
                    val highlightRange = TextRange(
                        dot?.startOffsetInParent ?: 0,
                        lValue.textLength
                    )

                    holder.registerProblem(
                        lValue,
                        "Invalid item",
                        ProblemHighlightType.ERROR,
                        highlightRange,
                        RemoveLValueItem(lValue)
                    )
                }
            }
        }
    }

    class RemoveLValueItem(lvalue: LslLValue) : LocalQuickFixOnPsiElement(lvalue) {
        override fun getFamilyName(): String = "Remove item"
        override fun getText(): String = familyName

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            val lValue = startElement as? LslLValue ?: return
            val dot = lValue.dot ?: return
            lValue.deleteChildRange(dot, lValue.lastChild)
        }
    }
}