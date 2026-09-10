package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElementVisitor
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslStatementJump

class LslUndeclaredLabelInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Undeclared label"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        // 1. Fetch file and preprocessor service ONCE per inspection pass
        val file = holder.file
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {
            override fun visitStatementJump(statementJump: LslStatementJump) {
                // 2. Preprocessor check FIRST before evaluating jump statements
                if (preprocessorEngine.isDisabledText(file, statementJump.textRange)) return

                // 3. Guard check: only process non-empty jump statements
                if (statementJump.textRange.isEmpty) return

                // 4. Verify label reference resolves successfully
                if (statementJump.reference?.resolve() == null) {
                    val labelName = statementJump.labelNameIdentifier?.text ?: statementJump.text

                    // 5. Register problem for undeclared labels
                    holder.registerProblem(
                        statementJump,
                        "Undeclared label '$labelName'",
                        ProblemHighlightType.ERROR,
                        statementJump.labelNameIdentifier?.textRangeInParent
                        // TODO: create variable fix
                    )
                }
            }
        }
    }
}