package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslStatementState

class LslUndeclaredStateInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Undeclared state"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : LslElementVisitor() {
            override fun visitStatementState(statementState: LslStatementState) {
                // 1. Guard check: only process non-empty state statements
                if (statementState.textRange.isEmpty) return

                // 2. Verify state reference resolves successfully
                if (statementState.reference?.resolve() == null) {
                    val stateName = statementState.stateNameIdentifier?.text ?: statementState.text

                    holder.registerProblem(
                        statementState,
                        "Undeclared state '$stateName'",
                        ProblemHighlightType.ERROR,
                        statementState.stateNameIdentifier?.textRangeInParent
                        // TODO: create state fix
                    )
                }
            }
        }
    }
}