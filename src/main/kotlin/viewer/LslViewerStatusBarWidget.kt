package io.github.koollsl.lsl.viewer

import com.intellij.openapi.components.service
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.SwingConstants

class LslViewerStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = LslViewerStatusBarWidget.ID

    override fun getDisplayName(): String = "Second Life Viewer"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget =
        LslViewerStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) = widget.dispose()

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class LslViewerStatusBarWidget(
    private val project: Project
) : CustomStatusBarWidget {
    private val viewerIcon = IconLoader.getIcon("/icons/lslp.svg", LslViewerStatusBarWidget::class.java)
    private val label = JLabel("", SwingConstants.CENTER).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border = javax.swing.BorderFactory.createEmptyBorder(0, 6, 0, 6)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.button == MouseEvent.BUTTON1) showViewerManager(project)
            }
        })
    }
    private var removeConnectionStateListener: (() -> Unit)? = null

    override fun ID(): String = ID

    override fun getComponent(): JLabel = label

    override fun install(statusBar: StatusBar) {
        removeConnectionStateListener =
            project.service<LslViewerConnectionService>().addConnectionStateListener(::updatePresentation)
        updatePresentation()
    }

    override fun dispose() {
        removeConnectionStateListener?.invoke()
        removeConnectionStateListener = null
    }

    private fun updatePresentation() {
        if (project.isDisposed) return
        val connection = project.service<LslViewerConnectionService>()
        val text = when {
            connection.isConnected -> "Viewer: Connected"
            connection.isConnecting -> "Viewer: Connecting..."
            else -> "Viewer: Disconnected"
        }
        label.text = text
        label.icon = viewerIcon
        label.toolTipText = "$text — click to manage viewer objects and connection"
    }

    companion object {
        const val ID = "LslViewerConnectionStatus"
    }
}
