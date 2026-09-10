package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElementVisitor
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslExpressionFunctionCall

class LslUndeclaredFunctionInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Undeclared function"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and preprocessor service ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {
            override fun visitExpressionFunctionCall(expressionFunctionCall: LslExpressionFunctionCall) {
                // 2. Preprocessor check FIRST before evaluating function calls
                if (preprocessorEngine.isDisabledText(file, expressionFunctionCall.textRange)) return

                // 3. Guard check: process non-empty function calls
                if (expressionFunctionCall.textRange.isEmpty) return

                // 4. Locate function identifier
                val identifier = expressionFunctionCall.functionNameIdentifier ?: return

                // 5. Resolve reference from identifier OR call expression
                val reference = identifier.reference ?: expressionFunctionCall.reference

                // 6. Flag if no reference exists or resolution yields null
                if (reference == null || reference.resolve() == null) {
                    holder.registerProblem(
                        expressionFunctionCall,
                        "Undeclared function '${identifier.text}'",
                        ProblemHighlightType.ERROR,
                        identifier.textRangeInParent
                    )
                }
            }
        }
    }
}