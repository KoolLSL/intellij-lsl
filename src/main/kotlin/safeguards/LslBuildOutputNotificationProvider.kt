package io.github.koollsl.lsl.safeguards

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import io.github.koollsl.lsl.utils.LslFileTypeUtils.isBuildFile
import java.util.function.Function
import javax.swing.JComponent

class LslBuildOutputNotificationProvider : EditorNotificationProvider {
    companion object {
        const val WARNING_TEXT = "This is a generated build file. Direct changes will be overwritten during preprocessing."
    }

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        if (!isBuildFile(file)) return null

        return Function { fileEditor ->
            EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning).apply {
                text = WARNING_TEXT
            }
        }
    }
}