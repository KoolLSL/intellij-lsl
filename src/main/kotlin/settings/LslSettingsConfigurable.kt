package io.github.koollsl.lsl.settings

import KwdbData
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class LslSettingsConfigurable : Configurable {

    private val settings = LslSettings.instance
    private var panel: DialogPanel? = null

    override fun getDisplayName(): String = "LSL Settings"

    override fun createComponent(): JComponent {
        // Retrieve the active project safely without changing constructor parameters
        val activeProject = ProjectManager.getInstance().openProjects.firstOrNull()
        var sourceInfo = "No active project"
        var funcsCount = 0
        var constsCount = 0
        var eventsCount = 0

        if (activeProject != null) {
            try {
                val kwdb = KwdbData.getInstance(activeProject)
                sourceInfo = kwdb.kwdbSourceInfo.ifEmpty { "None" }
                funcsCount = kwdb.functions.size
                constsCount = kwdb.constants.size
                eventsCount = kwdb.events.size
            } catch (e: Throwable) {
                sourceInfo = "Error: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        val createdPanel = panel {
            row {
                checkBox("Constant optimization")
                    .bindSelected(settings::optimizeConstants)
                    .comment("""
                    Inlines constant values and pre-calculates math.<br>
                    Example: <code>integer HOUR = 3600; llSetTimer(24 * HOUR);<br>
                    becomes: llSetTimer(86400);</code></pre><br>
                    Enable for release to reduce scripts memory; disable when debugging.
                     """.trimIndent())
            }

            separator()

            // --- Keyword Database Status ---
            row {
                text("""<b>Keyword Database</b>""")

            }
            row("Source Loaded:") {
                label(sourceInfo)
            }
            row("Parsed:") {
                label("$funcsCount functions, $constsCount constants, $eventsCount events")
            }


            row {
                text("""
                    To update the Keyword Database (LSL functions, events, and constants), download a newer <code>lsl_definitions.yaml</code> file from <a href="https://github.com/secondlife/lsl-definitions">github.com/secondlife/lsl-definitions</a> and select it below.<br>
                    <i>Leave blank to use the plugin's original definitions. Restart IDE after changing.</i>
                """.trimIndent())
            }

            row("Custom lsl_definitions.yaml:") {
                val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
                    .withExtensionFilter("yaml", "yml")
                    .withTitle("Select lsl_definitions.yaml File")
                    .withDescription("Select custom lsl_definitions.yaml definition file")

                textFieldWithBrowseButton(
                    fileChooserDescriptor = descriptor
                )
                    .bindText(settings::customKwdbPath)
                    .columns(COLUMNS_LARGE)
            }

            separator()

            row {
                text("""
                    <b>Related Settings</b><br>
                    • To customize formatting rules, go to <b>Editor | Code Style | LSL</b>.<br>
                    • To adjust syntax highlighting, go to <b>Editor | Color Scheme | LSL</b>.
                """.trimIndent())
            }
        }

        panel = createdPanel
        return createdPanel
    }

    override fun isModified(): Boolean = panel?.isModified() ?: false

    override fun apply() {
        panel?.apply()
    }

    override fun reset() {
        panel?.reset()
    }
}