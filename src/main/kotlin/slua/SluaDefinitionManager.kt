package io.github.koollsl.lsl.slua

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

object SluaDefinitionManager {

    /**
     * Ensures secondlife.d.luau exists on disk and returns its VirtualFile reference.
     */
    fun ensureDefinitionsAvailable(project: Project): VirtualFile? {
        val data = SluaDefinitionData.getInstance(project)
        val file: File = data.definitionsFile

        if (!file.exists()) return null

        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
    }

    /**
     * Helper to retrieve the absolute path for passing directly to the LSLP LSP server process configuration.
     */
    fun getDefinitionFilePath(project: Project): String {
        return SluaDefinitionData.getInstance(project).definitionsFile.absolutePath
    }
}