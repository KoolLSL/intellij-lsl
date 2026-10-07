package io.github.koollsl.lsl.slua

import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

enum class LuauPluginStatus(val displayName: String) {
    TO_INSTALL("To install"),
    INSTALLED("Installed"),
    DISABLED("Disabled")
}

// Displays a banner at the top of .luau files if the required Luau IntelliJ plugin is disabled or missing.
class SluaEditorNotificationProvider : EditorNotificationProvider {

    companion object {
        const val LUAU_PLUGIN_ID = "com.github.aleksandrsl.intellijluau"
        const val LUAU_PLUGIN_NAME = "Luau"
        const val LUAU_PLUGIN_MARKETPLACE_URL =
            "https://plugins.jetbrains.com/plugin/24957"

        fun getLuauPluginStatus(): LuauPluginStatus {
            val plugin = PluginManagerCore.getPlugin(PluginId.getId(LUAU_PLUGIN_ID))
                ?: return LuauPluginStatus.TO_INSTALL
            return if (plugin.isEnabled) LuauPluginStatus.INSTALLED else LuauPluginStatus.DISABLED
        }
    }

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {

        // Only process .luau script files
        if (!file.extension.equals("luau", ignoreCase = true)) return null

        // Case 1: The plugin is installed but disabled—offer a quick link to settings to enable it
        val pluginStatus = getLuauPluginStatus()
        if (pluginStatus == LuauPluginStatus.DISABLED) {
            return Function {
                val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Warning)
                panel.text = "$LUAU_PLUGIN_NAME plugin is disabled. Enable it to get syntax highlighting and language server analysis for .luau scripts."
                panel.createActionLabel("Open Plugins settings") {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, "Plugins")
                }
                panel
            }
        }

        if (pluginStatus == LuauPluginStatus.INSTALLED && SluaServerConfigurationChecker.hasOutdatedPaths()) {
            return Function {
                val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Warning)
                panel.text =
                    "Luau Language Server is configured with outdated Second Life definition paths. Update its command using the paths in LSL settings."
                panel.createActionLabel("Show correct paths") {
                    ShowSettingsUtil.getInstance().showSettingsDialog(
                        project,
                        io.github.koollsl.lsl.shared.settings.LslSettingsConfigurable::class.java
                    )
                }
                panel
            }
        }

        if (pluginStatus == LuauPluginStatus.INSTALLED) return null

        // Case 2: The plugin is missing entirely—prompt the user to open the Marketplace and install it
        return Function {
            val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Info)
            panel.text = "$LUAU_PLUGIN_NAME support is not installed. Install the $LUAU_PLUGIN_NAME plugin to enable code intelligence."
            panel.createActionLabel("Open Marketplace") {
                BrowserUtil.browse(LUAU_PLUGIN_MARKETPLACE_URL)
            }
            panel
        }
    }
}