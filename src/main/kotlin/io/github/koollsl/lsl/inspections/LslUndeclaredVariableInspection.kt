package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElementVisitor
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslLValue

class LslUndeclaredVariableInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Undeclared variable"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and preprocessor service ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {
            override fun visitLValue(lValue: LslLValue) {

                // 2. Preprocessor check FIRST before evaluating L-values
                if (preprocessorEngine.isDisabledText(file, lValue.textRange)) return

                // 3. Guard check: only process non-empty L-values (variables)
                if (lValue.textRange.isEmpty) return

                // 4. Verify variable reference resolves successfully
                if (lValue.reference?.resolve() == null) {
                    val variableName = lValue.variableNameIdentifier?.text ?: lValue.text

                    // 5. Register problem for undeclared variables
                    holder.registerProblem(
                        lValue,
                        "Undeclared variable '$variableName'",
                        ProblemHighlightType.ERROR,
                        lValue.variableNameIdentifier?.textRangeInParent
                        // TODO: create variable fix
                    )
                }
            }
        }
    }
}