package io.github.koollsl.lsl.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine

class OpenProcessedLslAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = getTargetFile(e)

        if (project == null || file == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val ext = file.extension?.lowercase()
        val isLslp = ext == "lslp"
        val isLsl = ext == "lsl"

        if (!isLslp && !isLsl) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        e.presentation.isEnabledAndVisible = true

        if (isLslp) {
            val targetFile = findProcessedLslFile(project, file)
            val lslFileName = "'${file.nameWithoutExtension}.lsl'"

            if (targetFile != null) {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val isOpen = fileEditorManager.isFileOpen(targetFile)

                if (isOpen) {
                    e.presentation.text = "Go to Built $lslFileName"
                    e.presentation.description = "Switch focus to the open $lslFileName tab"
                } else {
                    e.presentation.text = "Open Built $lslFileName"
                    e.presentation.description = "Open built $lslFileName in a new tab"
                }
            } else {
                e.presentation.text = "Build and Open $lslFileName"
                e.presentation.description = "Run preprocessor on '${file.name}' and open built $lslFileName"
            }
        } else {
            // File is an .lsl output file -> look for corresponding .lslp source
            val targetFile = findSourceLslpFile(project, file)
            val lslpFileName = "'${file.nameWithoutExtension}.lslp'"

            if (targetFile != null) {
                e.presentation.isEnabled = true
                val fileEditorManager = FileEditorManager.getInstance(project)
                val isOpen = fileEditorManager.isFileOpen(targetFile)

                if (isOpen) {
                    e.presentation.text = "Go to Source $lslpFileName"
                    e.presentation.description = "Switch focus to the open $lslpFileName tab"
                } else {
                    e.presentation.text = "Open Source $lslpFileName"
                    e.presentation.description = "Open source $lslpFileName in a new tab"
                }
            } else {
                e.presentation.isEnabled = false
                e.presentation.text = "No Source $lslpFileName Found"
                e.presentation.description = "Cannot find corresponding $lslpFileName source file"
            }
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val currentFile = getTargetFile(e) ?: return

        val ext = currentFile.extension?.lowercase()
        val targetFile = if (ext == "lslp") {
            findProcessedLslFile(project, currentFile)
        } else if (ext == "lsl") {
            findSourceLslpFile(project, currentFile)
        } else {
            null
        }

        // 1. Focus the right-clicked tab first using navigate(true)
        OpenFileDescriptor(project, currentFile).navigate(true)

        // 2. Open or switch to the target file next to it
        if (targetFile != null) {
            openTargetInNewTab(project, targetFile)
        } else if (ext == "lslp") {
            compileAndOpen(project, currentFile)
        }
    }

    private fun openTargetInNewTab(
        project: Project,
        targetFile: VirtualFile
    ) {
        val descriptor = OpenFileDescriptor(project, targetFile)
        descriptor.isUseCurrentWindow = false
        descriptor.navigate(true)
    }

    private fun compileAndOpen(project: Project, lslpFile: VirtualFile) {
        // 1. Trigger preprocessor execution directly
        LslPreprocessorEngine(project).processFileOnSave(lslpFile)

        lslpFile.parent?.findChild("build")?.refresh(false, false)

        val newTargetFile = findProcessedLslFile(project, lslpFile)
        if (newTargetFile != null) {
            openTargetInNewTab(project, newTargetFile)
        }
    }

    private fun getTargetFile(e: AnActionEvent): VirtualFile? {
        val selectedItem = e.getData(PlatformCoreDataKeys.SELECTED_ITEM)
        if (selectedItem is VirtualFile) {
            return selectedItem
        }
        return e.getData(CommonDataKeys.VIRTUAL_FILE)
    }

    private fun findProcessedLslFile(project: Project, lslpFile: VirtualFile): VirtualFile? {
        val lslFileName = "${lslpFile.nameWithoutExtension}.lsl"
        return lslpFile.parent?.findChild("build")?.findChild(lslFileName)?.takeIf { it.exists() }
    }

    private fun findSourceLslpFile(project: Project, lslFile: VirtualFile): VirtualFile? {
        val lslpFileName = "${lslFile.nameWithoutExtension}.lslp"
        // If current file is in ./build/, parent.parent points to the source root
        val buildDir = lslFile.parent
        val sourceDir = if (buildDir?.name?.equals("build", ignoreCase = true) == true) {
            buildDir.parent
        } else {
            buildDir
        }
        return sourceDir?.findChild(lslpFileName)?.takeIf { it.exists() }
    }
}