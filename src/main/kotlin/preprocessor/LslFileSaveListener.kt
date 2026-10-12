package io.github.koollsl.lsl.preprocessor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import io.github.koollsl.lsl.utils.LslFileTypeUtils

@Service(Service.Level.PROJECT)
class LslFileSaveListener(private val project: Project) : FileDocumentManagerListener {

    override fun beforeDocumentSaving(document: Document) {
        // Do nothing if file is explicitly excluded in IDE, or not part of THIS project
        val file: VirtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
        val fileIndex = ProjectRootManager.getInstance(project).fileIndex
        if (fileIndex.isExcluded(file) || !fileIndex.isInContent(file)) {
            return
        }

        if (!LslFileTypeUtils.isLslSource(file)) return
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                WriteCommandAction.runWriteCommandAction(project, "LSL Preprocess File", null, Runnable {
                    PsiDocumentManager.getInstance(project).commitDocument(document)

                    val engine = project.service<LslPreprocessorEngine>()
                    engine.processFileOnSave(file)
                })
            }
        }
    }
}
