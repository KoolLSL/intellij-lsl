package io.github.koollsl.lsl.slua

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
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

        // Shared global location in the IDE system directory so all open projects can reuse the same definition file
        val globalDefinitionsFile: File by lazy {
            val pluginDir = File(PathManager.getSystemPath(), "lsl-intellij")
            if (!pluginDir.exists()) {
                pluginDir.mkdirs()
            }
            File(pluginDir, "secondlife.d.luau")
        }
    }

    init {
        definitionsFile = globalDefinitionsFile

        // Extract the definition file bundled inside the plugin JAR out to disk if it hasn't been extracted yet
        if (!definitionsFile.exists()) {
            val stream = javaClass.classLoader.getResourceAsStream("secondlife.d.luau")
                ?: javaClass.getResourceAsStream("/secondlife.d.luau")
                ?: throw IllegalStateException("secondlife.d.luau resource stream could not be obtained.")

            stream.use { input ->
                definitionsFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        luauDefinitionsText = definitionsFile.readText()
    }
}