package io.github.koollsl.lsl.definitions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import io.github.koollsl.lsl.settings.LslSettings

// Startup activity that runs asynchronously when a project opens, ensuring Luau API definitions exist on disk.
class SluaDefinitionInitializer : ProjectActivity {
    override suspend fun execute(project: Project) {
        SluaDefinitionManager.ensureDefinitionsAvailable(project)

        if (ApplicationManager.getApplication().isUnitTestMode) return
        if (!LslSettings.instance.useGithubDefinitions) return

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = DefinitionsSourceManager.updateFromGithubIfDue()
                if (result.failures.isNotEmpty()) {
                    LOG.warn(
                        "Some LSL/SLua definitions could not be updated: " +
                            result.failures.entries.joinToString { "${it.key}: ${it.value}" }
                    )
                }
                if (result.updatedFiles.isNotEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) {
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup("LSL/SLua Definitions")
                                .createNotification(
                                    "LSL/SLua Definitions Updated",
                                    "Definitions updated (${result.updatedFiles.sorted().joinToString()}). Restart the IDE to load them.",
                                    NotificationType.INFORMATION
                                )
                                .addAction(
                                    NotificationAction.createSimple("Restart IDE") {
                                        ApplicationManagerEx.getApplicationEx().restart()
                                    }
                                )
                                .notify(project)
                        }
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Could not update LSL/SLua definitions from GitHub", e)
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(SluaDefinitionInitializer::class.java)
    }
}