package io.github.koollsl.lsl.references

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PsiTreeUtil
import io.github.koollsl.lsl.parser.LslTypes
import io.github.koollsl.lsl.psi.ASTWrapperLslNamedElement
import io.github.koollsl.lsl.psi.LslFunction
import io.github.koollsl.lsl.psi.LslGlobalVariable

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

        val vFile = file.virtualFile ?: return emptyList()
        if (ProjectRootManager.getInstance(file.project).fileIndex.isExcluded(vFile)) {
            return emptyList()
        }
        val functions = PsiTreeUtil.findChildrenOfType(file, LslFunction::class.java)
        val globalVars = PsiTreeUtil.findChildrenOfType(file, LslGlobalVariable::class.java)
        val targets = (functions + globalVars).filterIsInstance<ASTWrapperLslNamedElement>()

        if (targets.isEmpty()) return emptyList()

        // 1. Build a fast lookup map: Name -> List of target PSI declarations
        val targetMap = targets.groupBy { it.name }
            .filterKeys { !it.isNullOrEmpty() } as Map<String, List<ASTWrapperLslNamedElement>>
        val usageCounts = targets.associateWith { 0 }.toMutableMap()

        val isModule = file.virtualFile?.extension?.equals("lslm", ignoreCase = true) == true

        if (isModule) {
            // For .lslm files across files, use fast word search without resolving references
            val searchScope = getLslSearchScope(file)
            for (target in targets) {
                val name = target.name ?: continue
                var count = 0
                PsiSearchHelper.getInstance(file.project).processElementsWithWord(
                    { element, _ ->
                        if (element.node.elementType == LslTypes.IDENTIFIER) {
                            val parentNamed =
                                PsiTreeUtil.getParentOfType(element, ASTWrapperLslNamedElement::class.java)
                            if (parentNamed != target) {
                                count++
                            }
                        }
                        true
                    },
                    searchScope,
                    name,
                    UsageSearchContext.IN_CODE,
                    true
                )
                usageCounts[target] = count
            }
        } else {
            // 2. High-speed single pass for standard files: Count matching leaf tokens directly
            PsiTreeUtil.processElements(file) { element ->
                if (element.node.elementType == LslTypes.IDENTIFIER) {
                    val text = element.text
                    val matchingTargets = targetMap[text]
                    if (matchingTargets != null) {
                        val parentDeclaration =
                            PsiTreeUtil.getParentOfType(element, ASTWrapperLslNamedElement::class.java)
                        for (target in matchingTargets) {
                            // Increment count if this leaf isn't the declaration's own identifier
                            if (parentDeclaration != target) {
                                usageCounts[target] = (usageCounts[target] ?: 0) + 1
                            }
                        }
                    }
                }
                true
            }
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

    private fun getLslSearchScope(file: PsiFile): LocalSearchScope {
        val virtualFile = file.virtualFile ?: return LocalSearchScope(file)
        val project = file.project
        val fileName = virtualFile.name

        val matchingPsiFiles = mutableListOf<PsiFile>()
        matchingPsiFiles.add(file)

        val searchContext = (UsageSearchContext.IN_PLAIN_TEXT.toInt() or UsageSearchContext.IN_CODE.toInt()).toShort()
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex

        PsiSearchHelper.getInstance(project).processElementsWithWord(
            { element, _ ->
                val containingFile = element.containingFile
                val targetVFile = containingFile?.virtualFile

                if (containingFile != null &&
                    targetVFile != null &&
                    !fileIndex.isExcluded(targetVFile) &&
                    containingFile !in matchingPsiFiles
                ) {
                    matchingPsiFiles.add(containingFile)
                }
                true
            },
            GlobalSearchScope.getScopeRestrictedByFileTypes(
                GlobalSearchScope.projectScope(project),
                file.fileType
            ),
            fileName,
            searchContext,
            true
        )

        return LocalSearchScope(matchingPsiFiles.toTypedArray())
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