package io.github.koollsl.lsl.slua

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.Messages
import io.github.koollsl.lsl.shared.definitions.DefinitionsSourceManager
import io.github.koollsl.lsl.shared.settings.LslSettings

// Startup activity that runs asynchronously when a project opens, ensuring Luau API definitions exist on disk.
class SluaDefinitionInitializer : ProjectActivity {
    override suspend fun execute(project: Project) {
        SluaDefinitionManager.ensureDefinitionsAvailable(project)

        if (!LslSettings.instance.useGithubDefinitions) return

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = DefinitionsSourceManager.updateFromGithubIfDue()
                if (result.failures.isNotEmpty()) {
                    LOG.warn(
                        "Some Second Life definitions could not be updated: " +
                            result.failures.entries.joinToString { "${it.key}: ${it.value}" }
                    )
                }
                if (result.updatedFiles.isNotEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed && Messages.showYesNoDialog(
                                project,
                                "Definitions updated (${result.updatedFiles.sorted().joinToString()}). Restart the IDE to load them?",
                                "Definitions Updated",
                                "Restart",
                                "Later",
                                null
                            ) == Messages.YES
                        ) {
                            ApplicationManagerEx.getApplicationEx().restart()
                        }
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Could not update Second Life definitions from GitHub", e)
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(SluaDefinitionInitializer::class.java)
    }
}