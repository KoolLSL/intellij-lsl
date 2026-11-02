package io.github.koollsl.lsl.shared.settings

import KwdbData
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import io.github.koollsl.lsl.shared.definitions.DefinitionsSourceManager
import io.github.koollsl.lsl.slua.LuauPluginStatus
import io.github.koollsl.lsl.slua.SluaEditorNotificationProvider
import io.github.koollsl.lsl.slua.SluaServerConfigurationChecker
import io.github.koollsl.lsl.viewer.LslViewerStatusBarWidgetFactory
import java.awt.Color
import java.io.IOException
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextArea

class LslSettingsConfigurable : SearchableConfigurable {

    private val settings = LslSettings.instance
    private var panel: DialogPanel? = null

    override fun getId(): String = "io.github.koollsl.lsl.settings.LslSettingsConfigurable"

    override fun getDisplayName(): String = "LSL"

    override fun createComponent(): JComponent {
        val activeProject = ProjectManager.getInstance().openProjects.firstOrNull()
        var funcsCount = 0
        var constsCount = 0
        var eventsCount = 0

        val lastCheck = DefinitionsSourceManager.getLastCheckDescription()

        val sluaDefinitionsInfo = DefinitionsSourceManager.getLuauDefinitionsInfo()
        val sluaDocsInfo = DefinitionsSourceManager.getLuauDocsInfo()
        val luauPluginStatus = SluaEditorNotificationProvider.getLuauPluginStatus()
        val sluaServerConfig = JTextArea(sluaServerConfiguration(), 4, 80).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
        }
        val sluaServerPathsOutdated = SluaServerConfigurationChecker.hasOutdatedPaths()

        val updateStatusLabel = JLabel("Last check: $lastCheck")

        var githubSourceButton: Cell<JBRadioButton>? = null
        var customFolderButton: Cell<JBRadioButton>? = null
        var viewerIntegrationCheckBox: Cell<JBCheckBox>? = null

        var lslDefinitionsInfo = DefinitionsSourceManager.getLslDefinitionsInfo()
        if (activeProject != null) {
            try {
                val kwdb = KwdbData.getInstance(activeProject)
                lslDefinitionsInfo = lslDefinitionsInfo.copy(
                    source = kwdb.kwdbSourceInfo.ifEmpty { lslDefinitionsInfo.source }
                )
                funcsCount = kwdb.functions.size
                constsCount = kwdb.constants.size
                eventsCount = kwdb.events.size
            } catch (_: Throwable) {
                lslDefinitionsInfo = lslDefinitionsInfo.copy(source = "unknown")
            }
        }

        val createdPanel = panel {
            row {
                checkBox("Constants optimization (LSL)")
                    .bindSelected(settings::optimizeConstants)
                    .comment("""
                        Inlines constant values and pre-calculates math. Enable to reduce scripts memory.<br>
                        Example: <code>integer HOUR = 3600; llSetTimer(24 * HOUR);</code> becomes: <code>llSetTimer(86400);</code><br>                        
                     """.trimIndent())
            }

            separator()
            row {
                checkBox("Enable Second Life viewer's objects")
                    .bindSelected(settings::viewerIntegrationEnabled)
                    .also { viewerIntegrationCheckBox = it }
                    .comment("Connect to the viewer's Script Editor Server to manually update in-world object scripts that have matching project scripts. Click the Viewer widget in the bottom bar to start.")
            }
            row("Viewer WebSocket port:") {
                intTextField(1..65535)
                    .bindIntText(settings::viewerWebSocketPort)
                    .enabledIf(viewerIntegrationCheckBox!!.selected)
            }

            separator()

            row {
                text("<b>Source of LSL & SLua definitions</b>")
            }
            row {
                comment(
                    "GitHub mode checks for new definitions once a day (recommended). " +
                            "If using the custom folder, it must contain lsl_definitions.yaml, secondlife.d.luau and secondlife.docs.json. " +
                            "Restart the IDE after changing the source."
                )
            }

            buttonsGroup {
                row {
                    radioButton("Auto-update from GitHub", false).also {
                        githubSourceButton = it
                    }
                    button("Check now") {
                        updateStatusLabel.text = "Checking GitHub for definition updates..."
                        ApplicationManager.getApplication().executeOnPooledThread {
                            try {
                                val result = DefinitionsSourceManager.updateFromGithubIfDue(force = true)
                                val lastCheckStr = DefinitionsSourceManager.getLastCheckDescription()

                                val message = buildString {
                                    when {
                                        result.failures.isNotEmpty() ->
                                            appendLine("Check completed with errors.")

                                        result.updatedFiles.isEmpty() ->
                                            appendLine("Definitions are already up to date.")

                                        else ->
                                            appendLine("Updated: ${result.updatedFiles.sorted().joinToString()}")
                                    }
                                    append("Last check: $lastCheckStr")
                                    if (result.failures.isNotEmpty()) {
                                        appendLine()
                                        appendLine("Some files could not be checked:")
                                        result.failures.forEach { (name, reason) ->
                                            appendLine("$name: $reason")
                                        }
                                    }
                                    if (customFolderButton?.component?.isSelected == true) {
                                        appendLine()
                                        append("Custom-folder mode is selected; downloaded files are cached but not active.")
                                    }
                                }
                                ApplicationManager.getApplication().invokeLater {
                                    updateStatusLabel.text =
                                        "Last check: $lastCheckStr"
                                    if (result.updatedFiles.isNotEmpty() && githubSourceButton?.component?.isSelected == true) {
                                        if (Messages.showYesNoDialog(
                                                message + "\n\nRestart the IDE now to use the updated definitions?",
                                                "Definitions Updated",
                                                "Restart",
                                                "Later",
                                                null
                                            ) == Messages.YES
                                        ) {
                                            ApplicationManagerEx.getApplicationEx().restart()
                                        }
                                    } else {
                                        Messages.showInfoMessage(message, "Definitions Update")
                                    }
                                }
                            } catch (e: Exception) {
                                ApplicationManager.getApplication().invokeLater {
                                    Messages.showErrorDialog(
                                        e.message ?: e.javaClass.simpleName,
                                        "Definitions Update Failed"
                                    )
                                }
                            }
                        }
                    }.enabledIf(githubSourceButton!!.selected)
                    cell(updateStatusLabel).enabledIf(githubSourceButton!!.selected)
                    browserLink("View on secondlife's GitHub", "https://github.com/secondlife/lsl-definitions")
                }

                row {
                    radioButton("Use a custom folder", true).also {
                        customFolderButton = it
                    }

//                    label("Custom folder:")
//                        .enabledIf(customFolderButton!!.selected)

                    val descriptor = object : FileChooserDescriptor(
                        true,
                        true,
                        false,
                        false,
                        false,
                        false
                    ) {
                        override fun validateSelectedFiles(files: Array<VirtualFile>) {
                            if (files.any { !it.isDirectory }) {
                                throw IOException("Please select a folder.")
                            }
                        }
                    }
                        .withTitle("Select Definitions Folder")
                        .withDescription("Select a folder containing the Second Life definitions")
                        .withShowHiddenFiles(true)
                        .also { it.setForcedToUseIdeaFileChooser(true) }

                    textFieldWithBrowseButton(fileChooserDescriptor = descriptor)
                        .bindText(settings::customDefinitionsFolder)
                        .columns(COLUMNS_LARGE)
                        .enabledIf(customFolderButton!!.selected)
                }
            }.bind(settings::useCustomDefinitionsFolder)

            row("LSL definitions:") {
                label(
                    "${lslDefinitionsInfo.filename} (${lslDefinitionsInfo.source}, ${lslDefinitionsInfo.datetime}), " +
                            "$funcsCount functions, $constsCount constants, $eventsCount events"
                )
            }
            row("SLua definitions:") {
                label(
                    "${sluaDefinitionsInfo.filename} (${sluaDefinitionsInfo.source}, ${sluaDefinitionsInfo.datetime})"
                )
            }
            row("SLua documentation:") {
                label("${sluaDocsInfo.filename} (${sluaDocsInfo.source}, ${sluaDocsInfo.datetime})")
            }

            separator()

            row {
                text("<b>SLua language support</b>")
            }
            row("Luau plugin:") {
                val (symbol, color) = when (luauPluginStatus) {
                    LuauPluginStatus.INSTALLED ->
                        "✓" to JBColor.namedColor(
                            "Label.successForeground",
                            JBColor(Color(0x2E7D32), Color(0x81C784))
                        )
                    LuauPluginStatus.DISABLED ->
                        "⚠" to JBColor.namedColor(
                            "Label.warningForeground",
                            JBColor(Color(0xB26A00), Color(0xFFB74D))
                        )
                    LuauPluginStatus.TO_INSTALL ->
                        "⚠" to JBColor.namedColor(
                            "Label.errorForeground",
                            JBColor(Color(0xB3261E), Color(0xF28B82))
                        )
                }
                label("$symbol ${luauPluginStatus.displayName}").apply {
                    component.foreground = color
                }
                browserLink(
                    "View on JetBrains Marketplace",
                    SluaEditorNotificationProvider.LUAU_PLUGIN_MARKETPLACE_URL
                )
            }
            row {
                comment(
                    """
    For SLua editing, the <code>Luau</code> plugin by Aleksandr Slepchenkov is required. It automatically installs the following components:
    <ul>
    <li><a href="https://github.com/JohnnyMorganz/luau-lsp">luau-lsp</a> by Johnny Morganz
    (the same Luau engine used by Second Life's official VS Code extension).</li>
    <li><a href="https://plugins.jetbrains.com/plugin/23257-lsp4ij">LSP4IJ</a> by Red Hat (to enable Language Servers on IntelliJ free edition)</li>
    </ul>
    Once installed, verify in
    <code>Languages &amp; Frameworks / Language Servers</code>
    that for <code><b>Luau Language Server</b></code> the <code>Server</code> and <code>Installer</code> are both set to these files:
    """.trimIndent()
                )
            }
            row {
                cell(sluaServerConfig)
            }
            if (sluaServerPathsOutdated) {
                row {
                    comment(
                        "The Luau Language Server settings contain different definition paths. " +
                                "Update its command to use the paths shown above, then restart the language server."
                    )
                }
            }

            separator()

            row {
                text("""
                    <b>Related Settings</b><br>
                    • Search for <b>LSL</b> to adjust Code Style, Color Scheme, Inspections, Inlay Hints...
                """.trimIndent())
            }
        }

        panel = createdPanel
        return createdPanel
    }

    private fun sluaServerConfiguration(): String {
        val definitionsPath = DefinitionsSourceManager.getLuauDefinitionsFile()
            .absolutePath.replace('\\', '/')
        val docsPath = DefinitionsSourceManager.getLuauDocsFile()
            .absolutePath.replace('\\', '/')

        return """
          --definitions:@sl-slua=$definitionsPath 
          --docs=$docsPath
        """.trimIndent()

//        return """
//          "onSuccess": {
//            "configureServer": {
//              "name": "Configure luau-lsp server command",
//              "command": "${'$'}{output.dir}/${'$'}{output.file.name} lsp --definitions:@sl-slua=$definitionsPath --docs=$docsPath",
//              "update": true
//            }
//          }
//        """.trimIndent()
    }

    override fun isModified(): Boolean {
        return panel?.isModified() ?: false
    }

    override fun apply() {
        panel?.apply()
        ApplicationManager.getApplication()
            .getService(StatusBarWidgetsManager::class.java)
            .updateWidget(LslViewerStatusBarWidgetFactory::class.java)
    }

    override fun reset() {
        panel?.reset()
    }
}