package io.github.koollsl.lsl.slua

import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.Language
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

// Displays a banner at the top of .luau files if the required Luau IntelliJ plugin is disabled or missing.
class SluaEditorNotificationProvider : EditorNotificationProvider {

    companion object {
        private const val TARGET_PLUGIN_ID = "com.github.aleksandrsl.intellijluau"
        private const val TARGET_PLUGIN_NAME = "Luau"
    }

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {

        // Only process .luau script files
        if (!file.extension.equals("luau", ignoreCase = true)) return null

        // If Luau language support is already active in the IDE, no banner is needed
        if (Language.findLanguageByID("Luau") != null) {
            return null
        }

        val pluginId = PluginId.getId(TARGET_PLUGIN_ID)
        val pluginDescriptor = PluginManagerCore.getPlugin(pluginId)

        // Case 1: The plugin is installed but disabled—offer a quick link to settings to enable it
        if (pluginDescriptor != null && !pluginDescriptor.isEnabled) {
            return Function {
                val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Warning)
                panel.text = "$TARGET_PLUGIN_NAME plugin is disabled. Enable it to get syntax highlighting and language server analysis for .luau scripts."
                panel.createActionLabel("Enable $TARGET_PLUGIN_NAME") {
                    ShowSettingsUtil.getInstance().showSettingsDialog(
                        project,
                        PluginManagerConfigurable::class.java
                    ) { configurable ->
                        configurable.openMarketplaceTab(TARGET_PLUGIN_ID)
                    }
                }
                panel
            }
        }

        // Case 2: The plugin is missing entirely—prompt the user to open the Marketplace and install it
        return Function {
            val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Info)
            panel.text = "$TARGET_PLUGIN_NAME support is not installed. Install the $TARGET_PLUGIN_NAME plugin to enable code intelligence."
            panel.createActionLabel("Open Marketplace") {
                ShowSettingsUtil.getInstance().showSettingsDialog(
                    project,
                    PluginManagerConfigurable::class.java
                ) { configurable ->
                    configurable.openMarketplaceTab(TARGET_PLUGIN_ID)
                }
            }
            panel
        }
    }
}