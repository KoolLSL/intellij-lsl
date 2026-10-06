package io.github.koollsl.lsl.slua

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import io.github.koollsl.lsl.shared.definitions.DefinitionsSourceManager
import java.io.File

// Holds the Second Life Luau API definitions for a project, ensuring luau-lsp can access them on disk.
class SluaDefinitionData(val project: Project) {
    val definitionsFile: File
    val luauDefinitionsText: String

    companion object {
        // Caches this instance directly on the Project to avoid re-reading or re-extracting files unnecessarily
        private val SLUA_DATA_KEY = Key.create<SluaDefinitionData>("SLUA_DATA")

        fun getInstance(project: Project): SluaDefinitionData = project.getUserData(SLUA_DATA_KEY) ?: let {
            val data = SluaDefinitionData(project)
            project.putUserData(SLUA_DATA_KEY, data)
            data
        }

        val globalDefinitionsFile: File
            get() = DefinitionsSourceManager.getLuauDefinitionsFile()
    }

    init {
        definitionsFile = globalDefinitionsFile

        luauDefinitionsText = definitionsFile.readText()
    }
}