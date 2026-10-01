package io.github.koollsl.lsl.slua

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

// Startup activity that runs asynchronously when a project opens, ensuring Luau API definitions exist on disk.
class SluaDefinitionInitializer : ProjectActivity {
    override suspend fun execute(project: Project) {
        // Unpacks bundled definition files (if missing) so luau-lsp can immediately load them for autocompletion
        SluaDefinitionManager.ensureDefinitionsAvailable(project)
    }
}