package io.github.koollsl.lsl.references

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.psi.ASTWrapperLslNamedElement
import io.github.koollsl.lsl.psi.LslFile
import io.github.koollsl.lsl.psi.LslFunction
import io.github.koollsl.lsl.psi.LslGlobalVariable
import io.github.koollsl.lsl.references.LslReferenceUtils.getLslIncludeScope

class LslCodeVisionGroupSettingProvider : CodeVisionGroupSettingProvider {
    override val groupId: String = "lsl.code.usages"
    override val groupName: String = "LSL code Usages"
    override val description: String = "Shows usage counts on LSL functions and global variables"
}

class LslFunctionUsageCodeVisionProvider : DaemonBoundCodeVisionProvider {

    override val id: String = "lsl.code.usages"
    override val name: String = "LSL Code Usages"
    override val groupId: String = "lsl.code.usages"

    override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Right

    override val relativeOrderings: List<CodeVisionRelativeOrdering> = listOf(
        CodeVisionRelativeOrdering.CodeVisionRelativeOrderingFirst
    )

    override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
        // Upstream Guard: Exit immediately if the target file isn't an LSL file
        if (file !is LslFile) {
            return emptyList()
        }

        val vFile = file.virtualFile ?: return emptyList()
        if (ProjectRootManager.getInstance(file.project).fileIndex.isExcluded(vFile)) {
            return emptyList()
        }
        val engine = file.project.service<LslPreprocessorEngine>()

        val functions = PsiTreeUtil.findChildrenOfType(file, LslFunction::class.java)
        val globalVars = PsiTreeUtil.findChildrenOfType(file, LslGlobalVariable::class.java)

        // Filter out target declarations that are in disabled preprocessor blocks
        val targets = (functions + globalVars)
            .filterIsInstance<ASTWrapperLslNamedElement>()
            .filter { !engine.isElementDisabled(it) }

        if (targets.isEmpty()) return emptyList()

        val usageCounts = targets.associateWith { 0 }.toMutableMap()

        // 1. Single unified scope resolution (Module vs. Standard file)
        val searchScope = getLslIncludeScope(file)

        // 2. Single unified search loop for counting usages
        for (target in targets) {
            val name = target.name
            if (name.isNullOrEmpty()) continue

            // ReferencesSearch uses LslLValueReference / LslExpressionFunctionCallReference under the hood,
            // leveraging your ResolveCache and LslFileSymbolCache automatically.
//            val count = ReferencesSearch.search(target, searchScope)
//                .findAll()
//                .count { reference ->
//                    val element = reference.element
//                    !engine.isElementDisabled(element)
//                }
            val count = ReferencesSearch.search(target, searchScope)
                .filtering { reference -> !engine.isElementDisabled(reference.element) }
                .findAll()
                .size
            usageCounts[target] = count
        }

        // 3. Render Code Vision entries
        val visionEntries = mutableListOf<Pair<TextRange, CodeVisionEntry>>()
        for ((target, count) in usageCounts) {
            val range = target.nameIdentifier?.textRange ?: target.textRange ?: continue
            val text = if (count == 1) "1 usage" else "$count usages"

            val entry = TextCodeVisionEntry(
                text = text,
                providerId = id,
                icon = null,
                tooltip = "Number of usages",
                extraActions = emptyList()
            )

            visionEntries.add(Pair(range, entry))
        }

        return visionEntries
    }

    override fun handleClick(editor: Editor, textRange: TextRange, entry: CodeVisionEntry) {
        val project = editor.project ?: return
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return

        val elementAtCaret = file.findElementAt(textRange.startOffset) ?: return
        val namedElement = PsiTreeUtil.getParentOfType(
            elementAtCaret,
            ASTWrapperLslNamedElement::class.java
        )

        val targetOffset = namedElement?.nameIdentifier?.textRange?.startOffset ?: textRange.startOffset
        editor.caretModel.moveToOffset(targetOffset)

        val action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES) ?: return
        ActionManager.getInstance().tryToExecute(
            action,
            null,
            editor.contentComponent,
            null,
            true
        )
    }
}