package io.github.koollsl.lsl.inspections

import LslLanguage
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.LslElementVisitor
import io.github.koollsl.lsl.psi.LslState

class LslEmptyStateBodyInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "Empty state"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String =
        "Reports LSL state blocks that do not contain any event handlers."

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {

        // 1. Fetch service ONCE per inspection pass
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {
            override fun visitState(state: LslState) {
                // 1. Cheap guard check FIRST
                if (state.textLength == 0 || state.events.isNotEmpty()) return

                // 2. Preprocessor check ONLY on target candidates
                if (preprocessorEngine.isDisabledText(holder.file, state.textRange)) return

                // 3. Early return if left brace is missing (mid-typing)
                val brace = state.braceLeftEl ?: return

                // 4. Calculate relative range: from '{' to end of state block
                val startOffset = brace.startOffsetInParent
                val endOffset = state.textLength

                if (endOffset > startOffset) {
                    holder.registerProblem(
                        state,
                        "State has no events",
                        ProblemHighlightType.ERROR,
                        TextRange(startOffset, endOffset)
                    )
                }
            }
        }
    }
}