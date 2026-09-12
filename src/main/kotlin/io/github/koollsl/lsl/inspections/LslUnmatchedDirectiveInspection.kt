package io.github.koollsl.lsl.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import io.github.koollsl.lsl.LslLanguage
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine.SourceContext
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine.SourceDiagnostic
import io.github.koollsl.lsl.psi.LslElementVisitor

class LslUnmatchedDirectiveInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "Unmatched or unclosed preprocessor directive"
    override fun getGroupDisplayName(): String = LslLanguage.INSTANCE.displayName
    override fun isEnabledByDefault(): Boolean = true
    override fun getStaticDescription(): String = getDisplayName()


    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val preprocessorEngine = holder.project.service<LslPreprocessorEngine>()

        return object : LslElementVisitor() {
            override fun visitFile(file: PsiFile) {
                val ctx = SourceContext(
                    file = file,
                    project = file.project
                )

                val processed = preprocessorEngine.getCachedProcessDirectives(ctx)

                for (diagnostic in processed.diagnostics) {
                    val targetRange = getLineTextRange(file, diagnostic.lineNumber)
                    val description = when (diagnostic) {
                        is SourceDiagnostic.UnmatchedElseOrEndif ->
                            "Unmatched directive (missing opening #if...)"

                        is SourceDiagnostic.UnclosedBlockAtEof ->
                            "Unclosed block (missing '#endif')"

                        is SourceDiagnostic.UnknownDirective ->
                            "Unknown directive '${diagnostic.directive}'. Supported: if, ifdef, ifndef, else, elif, endif, define, undef, inline, include"

                        else -> null
                    }

                    if (description != null) {
                        holder.registerProblem(
                            file,
                            description,
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                            targetRange
                        )
                    }
                }
            }
        }
    }

    private fun getLineTextRange(file: PsiFile, lineNumber: Int): TextRange {
        val document = file.viewProvider.document ?: return file.textRange
        if (lineNumber < 1 || lineNumber > document.lineCount) return file.textRange

        val lineIndex = lineNumber - 1
        val startOffset = document.getLineStartOffset(lineIndex)
        val endOffset = document.getLineEndOffset(lineIndex)

        return TextRange(startOffset, endOffset)
    }
}