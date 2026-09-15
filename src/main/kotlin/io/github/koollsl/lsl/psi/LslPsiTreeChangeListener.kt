package io.github.koollsl.lsl.psi

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent

class LslPsiTreeChangeListener(private val project: Project) : PsiTreeChangeAdapter() {

    override fun childrenChanged(event: PsiTreeChangeEvent) = handleChange(event)
    override fun childReplaced(event: PsiTreeChangeEvent) = handleChange(event)
    override fun childAdded(event: PsiTreeChangeEvent) = handleChange(event)
    override fun childRemoved(event: PsiTreeChangeEvent) = handleChange(event)

    private fun handleChange(event: PsiTreeChangeEvent) {
        val psiFile = event.file as? LslFile ?: return
        val virtualFile = psiFile.virtualFile ?: return

        // 1. Update Project Tree View and Editor Tab Presentation
        ProjectView.getInstance(project).refresh()
        FileEditorManager.getInstance(project).updateFilePresentation(virtualFile)

        // 2. Direct refresh on preprocessor edits: Force immediate full-file re-highlighting
        val changedElement = event.parent ?: event.child ?: return
        if (changedElement.text.firstOrNull { !it.isWhitespace() } == '#') {
            DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
        }
    }
}