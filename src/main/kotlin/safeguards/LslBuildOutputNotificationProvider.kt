package io.github.koollsl.lsl.safeguards

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

class LslBuildOutputNotificationProvider : EditorNotificationProvider {
    companion object {
        const val WARNING_TEXT = "This is a generated build file. Direct changes will be overwritten during preprocessing."

        fun isGeneratedBuildFile(file: VirtualFile?): Boolean {
            if (file == null) return false
            val normalizedPath = file.path.replace('\\', '/')
            val dirPath = normalizedPath.substringBeforeLast('/', "")
            val segments = dirPath.split('/')
            return segments.any { segment ->
                segment.equals("build", ignoreCase = true) ||
                        segment.equals("out", ignoreCase = true) ||
                        segment.equals("output", ignoreCase = true) ||
                        segment.equals("dist", ignoreCase = true)
            }
        }

        @JvmStatic
        fun isGeneratedBuildLslFile(file: VirtualFile?): Boolean = isGeneratedBuildFile(file)
    }

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        if (!isGeneratedBuildFile(file)) return null

        return Function { fileEditor ->
            EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning).apply {
                text = WARNING_TEXT
            }
        }
    }
}