package io.github.koollsl.lsl.actions

import LslIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import io.github.koollsl.lsl.psi.LslFile
import io.github.koollsl.lsl.psi.LslStatementBlock

abstract class LslCreateFileActionBase(
    title: String,
    description: String,
    icon: javax.swing.Icon
) : CreateFileFromTemplateAction(title, description, icon), DumbAware {

    override fun postProcess(
        createdElement: PsiFile,
        templateName: String?,
        customProperties: MutableMap<String, String>?
    ) {
        super.postProcess(createdElement, templateName, customProperties)

        if (createdElement !is LslFile) return

        val project = createdElement.project
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val document = editor.document

        // Wait for the document to be committed and rendered in the editor
        ApplicationManager.getApplication().invokeLater {
            if (editor.isDisposed) return@invokeLater

            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)

            val statementBlock = PsiTreeUtil.findChildOfType(createdElement, LslStatementBlock::class.java)
            if (statementBlock != null) {
                // For LSL Source Scripts (.lslp): place caret inside state_entry block
                val brace = statementBlock.braceLeftEl
                if (brace != null) {
                    editor.caretModel.moveToOffset(brace.textRange.endOffset + 1)
                }
            } else {
                // For LSL Module Scripts (.lslm): place caret at the end of the header comment
                editor.caretModel.moveToOffset(document.textLength)
            }
        }
    }
}

// --- Action 1: Source Script (.lslp) ---
class LslCreateSourceFileAction : LslCreateFileActionBase(
    "LSL Source Script",
    "Creates a new LSL source file (.lslp)",
    LslIcons.FILE_LSLP
) {
    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder.setTitle("New LSL Source Script")
            .addKind("LSL Source Script", LslIcons.FILE_LSLP, "LSL Source Script")
    }

    override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String =
        "LSL Source Script"
}

// --- Action 2: Module Script (.lslm) ---
class LslCreateModuleFileAction : LslCreateFileActionBase(
    "LSL Module Script",
    "Creates a new LSL module file (.lslm)",
    LslIcons.FILE_LSLM
) {
    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder.setTitle("New LSL Module Script")
            .addKind("LSL Module Script", LslIcons.FILE_LSLM, "LSL Module Script")
    }

    override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String =
        "LSL Module Script"
}