package io.github.koollsl.lsl.viewer

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.IconLoader
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.util.ui.UIUtil
import io.github.koollsl.lsl.shared.settings.LslSettings
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.net.ConnectException
import java.util.Locale
import java.util.WeakHashMap
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTree
import javax.swing.ToolTipManager
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class LslViewerManagerAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        event.project?.let(::showViewerManager)
    }
}

internal fun showViewerManager(project: Project) {
    if (project.isDisposed) return
    val dialog = synchronized(viewerObjectsDialogs) {
        viewerObjectsDialogs[project]?.takeUnless { it.isDisposed }
            ?: ViewerObjectsDialog(project).also { viewerObjectsDialogs[project] = it }
    }
    if (dialog.isShowing) {
        dialog.toFront()
    } else {
        dialog.show()
    }
}

private val viewerObjectsDialogs = WeakHashMap<Project, ViewerObjectsDialog>()

private class ViewerObjectsDialog(
    private val project: Project
) : DialogWrapper(project) {
    private val connection = project.service<LslViewerConnectionService>()
    private val rootNode = DefaultMutableTreeNode("Explored Objects")
    private val treeModel = DefaultTreeModel(rootNode)
    private val inventoryTree = JTree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = ViewerObjectTreeCellRenderer()
        selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
    }
    private val connectionStatus = JLabel()
    private val connectionButton = JButton()
    private val informationHint = JLabel(
        "<html>Connect to the viewer's Script Editor Server to explore an object in IDE.<br>" +
            "Select the scripts to update into the object." +
            " Scripts with matching names must already exist into the object<br>" +
            "(i.e., 'build/Script A.lsl' in project matches 'Script A' in object, 'lib/Script B.luau' matches 'Script B')." +
             "</html>"
    ).apply {
        verticalAlignment = JLabel.TOP
        font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
        foreground = UIUtil.getContextHelpForeground()
        minimumSize = Dimension(0, 0)
    }
    private val activityLog = JTextArea().apply {
        isEditable = false
        lineWrap = false
    }
    private var lastConnected = connection.isConnected
    private var removeConnectionListener: (() -> Unit)? = null
    private var removeViewerStateListener: (() -> Unit)? = null
    private var dialogDisposed = false
    private var deploymentInProgress = false
    private var displayedObjects: List<ViewerObject>? = null
    private var mappingGeneration = 0L

    init {
        title = "Second Life Viewer Objects"
        setModal(false)
        setOKButtonText("Update selected scripts")
        setCancelButtonText("Close")
        connectionButton.addActionListener { toggleConnection() }
        inventoryTree.addTreeSelectionListener {
            updateDeployButton()
        }
        ToolTipManager.sharedInstance().registerComponent(inventoryTree)
        init()
        removeConnectionListener = connection.addConnectionStateListener(::connectionStateChanged)
        removeViewerStateListener = connection.addPublishedObjectsListener { objects ->
            updateObjects(objects)
            updateActivityLog(connection.getActivityLogSnapshot())
        }
        updateConnectionControls()
        updateObjects(connection.getPublishedObjects())
        updateActivityLog(connection.getActivityLogSnapshot())
        if (connection.isConnected) refreshObjects()
    }

    override fun createCenterPanel(): JPanel =
        JPanel(BorderLayout(0, 8)).apply {
            add(JPanel(BorderLayout(0, 6)).apply {
                add(informationHint, BorderLayout.NORTH)
                add(JPanel(BorderLayout(8, 0)).apply {
                    add(connectionStatus, BorderLayout.CENTER)
                    add(connectionButton, BorderLayout.EAST)
                }, BorderLayout.SOUTH)
            }, BorderLayout.NORTH)
            add(JScrollPane(inventoryTree).apply { preferredSize = Dimension(0, 440) }, BorderLayout.CENTER)
            add(JPanel(BorderLayout(0, 4)).apply {
                add(JLabel("Log"), BorderLayout.NORTH)
                add(JScrollPane(activityLog).apply { preferredSize = Dimension(0, 120) }, BorderLayout.CENTER)
            }, BorderLayout.SOUTH)
        }

    override fun doOKAction() {
        if (deploymentInProgress) return
        selectedPushRequest()?.let { request ->

            deploymentInProgress = true
            setOKActionEnabled(false)
            try {
                ApplicationManager.getApplication().runWriteAction {
                    FileDocumentManager.getInstance().saveAllDocuments()
                }
            } catch (error: Exception) {
                deploymentInProgress = false
                appendLog("Could not save documents: ${error.message ?: error.javaClass.simpleName}")
                updateDeployButton()
                return
            }

            val target = request.target
            appendLog(
                if (request.scripts == null) {
                    "Updating to object: ${target.objectName}"
                } else {
                    "Updating ${request.scripts.size} selected scripts to '${target.objectName}'"
                }
            )
            project.service<LslProjectDeploymentService>().launchMappedDeployment(
                target,
                ModalityState.stateForComponent(activityLog),
                selectedScripts = request.scripts
            ) {
                if (!project.isDisposed && !dialogDisposed) {
                    deploymentInProgress = false
                    refreshActivityLog()
                    updateDeployButton()
                }
            }
        }
    }

    override fun dispose() {
        dialogDisposed = true
        removeConnectionListener?.invoke()
        removeViewerStateListener?.invoke()
        removeConnectionListener = null
        removeViewerStateListener = null
        synchronized(viewerObjectsDialogs) {
            if (viewerObjectsDialogs[project] === this) viewerObjectsDialogs.remove(project)
        }
        super.dispose()
    }

    private fun selectedPushRequest(): ViewerPushRequest? {
        val paths = inventoryTree.selectionPaths ?: return null
        val selectedNodes = paths.mapNotNull { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode
            node?.let { it to (it.userObject as? ViewerTreeEntry) }
        }
        if (selectedNodes.size != paths.size || selectedNodes.isEmpty()) return null

        if (selectedNodes.size == 1) {
            val (node, entry) = selectedNodes.single()
            entry ?: return null
            if (entry.pushAllTarget != null) {
                return if (hasMappedScriptDescendant(node)) {
                    ViewerPushRequest(entry.pushAllTarget, null)
                } else {
                    null
                }
            }
            val script = entry.scriptSelection
            val target = entry.pushTarget
            if (script != null && target != null && entry.hasProjectMatch) {
                return ViewerPushRequest(target, listOf(script))
            }
            return null
        }

        val entries = selectedNodes.mapNotNull { it.second }
        if (entries.size != selectedNodes.size) return null
        if (entries.any { it.scriptSelection == null || it.pushTarget == null || !it.hasProjectMatch }) {
            return null
        }
        val targetIds = entries.mapNotNull { it.pushTarget?.objectId }.distinct()
        if (targetIds.size != 1) return null
        val target = entries.first().pushTarget ?: return null
        return ViewerPushRequest(target, entries.mapNotNull { it.scriptSelection })
    }

    private fun hasMappedScriptDescendant(node: DefaultMutableTreeNode): Boolean {
        val descendants = node.breadthFirstEnumeration()
        while (descendants.hasMoreElements()) {
            val child = descendants.nextElement() as? DefaultMutableTreeNode ?: continue
            val entry = child.userObject as? ViewerTreeEntry ?: continue
            if (entry.scriptSelection != null && entry.hasProjectMatch) return true
        }
        return false
    }

    private fun toggleConnection() {
        if (connection.isConnecting) return
        if (connection.isConnected) {
            connection.disconnect()
            return
        }
        connectionButton.isEnabled = false
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                connection.connect()
            } catch (error: Exception) {
                val message = connectionFailureMessage(error)
                appendLog("Connection failed: $message")
                showNotification(
                    project,
                    message,
                    NotificationType.WARNING
                )
            } finally {
                ApplicationManager.getApplication().invokeLater({
                    if (!project.isDisposed && isShowing) updateConnectionControls()
                }, ModalityState.any())
            }
        }
    }

    private fun connectionStateChanged() {
        if (project.isDisposed || dialogDisposed) return
        val connectedNow = connection.isConnected
        updateConnectionControls()
        if (connectedNow != lastConnected) {
            appendLog(
                if (connectedNow) {
                    "Connected to the viewer on port ${LslSettings.instance.viewerWebSocketPort}."
                } else {
                    "Disconnected from the viewer."
                }
            )
        }
        if (connectedNow && !lastConnected) refreshObjects()
        if (!connectedNow) updateObjects(emptyList())
        lastConnected = connectedNow
        updateDeployButton()
    }

    private fun updateConnectionControls() {
        val connected = connection.isConnected
        connectionStatus.text = when {
            connection.isConnecting -> "Connecting to the viewer..."
            connected -> {
                val regions = connection.getPublishedObjects()
                    .mapNotNull { it.region?.trim()?.takeIf(String::isNotEmpty) }
                    .distinct()
                when (regions.size) {
                    0 -> "Connected to the viewer — region unavailable"
                    1 -> "Connected to the viewer — ${regions.single()}"
                    else -> "Connected to the viewer — ${regions.joinToString(", ")}"
                }
            }
            else -> "Disconnected from the viewer"
        }
        connectionButton.text = when {
            connection.isConnecting -> "Connecting..."
            connected -> "Disconnect"
            else -> "Connect"
        }
        connectionButton.isEnabled = !connection.isConnecting
        updateDeployButton()
    }

    private fun refreshObjects() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val objects = connection.refreshPublishedObjects()
                ApplicationManager.getApplication().invokeLater({
                    if (!project.isDisposed && !dialogDisposed) updateObjects(objects)
                }, ModalityState.any())
            } catch (error: Exception) {
                appendLog(
                    "Could not refresh viewer objects: ${error.message ?: error.javaClass.simpleName}"
                )
                showNotification(
                    project,
                    error.message ?: error.javaClass.simpleName,
                    NotificationType.WARNING
                )
            }
        }
    }

    private fun updateObjects(objects: List<ViewerObject>) {
        if (project.isDisposed || dialogDisposed) return
        if (displayedObjects == objects) {
            updateDeployButton()
            return
        }
        displayedObjects = objects
        val selectedKeys = inventoryTree.selectionPaths.orEmpty().mapNotNull { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode
            (node?.userObject as? ViewerTreeEntry)?.selectionKey
        }.toSet()
        rootNode.removeAllChildren()
        objects.forEach { target ->
            val objectNode = DefaultMutableTreeNode(ViewerTreeEntry(
                "${target.objectName} (${target.objectId})",
                selectionKey = "object:${target.objectId}",
                pushTarget = target,
                pushAllTarget = target,
                icon = ViewerTreeIcon.OBJECT
            ))
            addInventory(objectNode, target, target.objectId, target.inventory)
            target.linkedObjects.forEach { linked ->
                val linkedNode = DefaultMutableTreeNode(ViewerTreeEntry(
                    "${linked.linkName} (${linked.linkId})",
                    selectionKey = "linked:${target.objectId}:${linked.linkId}",
                    pushTarget = target,
                    icon = ViewerTreeIcon.OBJECT
                ))
                addInventory(linkedNode, target, linked.linkId, linked.inventory)
                objectNode.add(linkedNode)
            }
            rootNode.add(objectNode)
        }
        if (rootNode.childCount == 0) {
            rootNode.add(DefaultMutableTreeNode("No explored objects"))
        }
        treeModel.reload()
        var expansionRow = 0
        while (expansionRow < inventoryTree.rowCount) {
            inventoryTree.expandRow(expansionRow)
            expansionRow++
        }
        val selectionPaths = (0 until inventoryTree.rowCount).mapNotNull { selectionRow ->
            val path = inventoryTree.getPathForRow(selectionRow) ?: return@mapNotNull null
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@mapNotNull null
            val key = (node.userObject as? ViewerTreeEntry)?.selectionKey
            if (key in selectedKeys) path else null
        }
        if (selectionPaths.isNotEmpty()) {
            inventoryTree.selectionPaths = selectionPaths.toTypedArray()
        }
        updateConnectionControls()
        updateDeployButton()
        refreshMappingIndicators()
    }

    private fun refreshMappingIndicators() {
        val scriptEntries = mutableListOf<ViewerTreeEntry>()
        val nodes = rootNode.breadthFirstEnumeration()
        while (nodes.hasMoreElements()) {
            val node = nodes.nextElement() as? DefaultMutableTreeNode ?: continue
            val entry = node.userObject as? ViewerTreeEntry ?: continue
            if (entry.scriptSelection != null) scriptEntries.add(entry)
        }
        if (scriptEntries.isEmpty()) return

        val generation = ++mappingGeneration
        val scriptNames = scriptEntries.mapNotNull { it.scriptSelection?.scriptName }.distinct()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val matches = project.service<LslProjectDeploymentService>()
                    .findProjectScriptMatches(scriptNames)
                ApplicationManager.getApplication().invokeLater({
                    if (project.isDisposed || dialogDisposed || generation != mappingGeneration) return@invokeLater
                    scriptEntries.forEach { entry ->
                        entry.projectMatch = entry.scriptSelection?.scriptName
                            ?.lowercase(Locale.ROOT)
                            ?.let(matches::get)
                        entry.mappingResolved = true
                    }
                    inventoryTree.repaint()
                    updateDeployButton()
                }, ModalityState.any())
            } catch (error: Exception) {
                connection.logActivity(
                    "Could not resolve project script matches: ${error.message ?: error.javaClass.simpleName}"
                )
            }
        }
    }

    private fun updateDeployButton() {
        val selectedScriptCount = selectedMappedScriptCount()
        setOKButtonText(
            if (selectedScriptCount == 0) "Update selected scripts"
            else "Update $selectedScriptCount scripts"
        )
        setOKActionEnabled(
            !deploymentInProgress && selectedPushRequest() != null && connection.isConnected
        )
    }

    private fun selectedMappedScriptCount(): Int {
        val paths = inventoryTree.selectionPaths ?: return 0
        val selectedNodes = paths.mapNotNull { path ->
            path.lastPathComponent as? DefaultMutableTreeNode
        }
        if (selectedNodes.size == 1) {
            val node = selectedNodes.single()
            val entry = node.userObject as? ViewerTreeEntry
            if (entry?.pushAllTarget != null) {
                val descendants = node.breadthFirstEnumeration()
                var count = 0
                while (descendants.hasMoreElements()) {
                    val child = descendants.nextElement() as? DefaultMutableTreeNode ?: continue
                    val childEntry = child.userObject as? ViewerTreeEntry ?: continue
                    if (childEntry.scriptSelection != null && childEntry.hasProjectMatch) count++
                }
                return count
            }
        }
        return selectedNodes.count { node ->
            val entry = node.userObject as? ViewerTreeEntry
            entry?.scriptSelection != null && entry.hasProjectMatch
        }
    }

    private fun addInventory(
        parent: DefaultMutableTreeNode,
        target: ViewerObject,
        primId: String,
        inventory: List<ViewerInventoryItem>
    ) {
        if (inventory.isEmpty()) {
            parent.add(DefaultMutableTreeNode("(empty)"))
            return
        }
        inventory.sortedBy { it.name.lowercase() }.forEach { item ->
            val isScript = item.type.equals("script", ignoreCase = true)
            val details = buildString {
                append(item.name)
                if (isScript) {
                    item.running?.let { append(if (it) " — running" else " — stopped") }
                    item.vm?.let { append(" — $it") }
                    item.nextOwnerPermissionFlags?.let { append(" — $it") }
                }
            }
            val icon = if (isScript) ViewerTreeIcon.SCRIPT else ViewerTreeIcon.ITEM
            val scriptSelection = if (isScript) {
                ViewerScriptSelection(primId, item.itemId, item.name)
            } else {
                null
            }
            parent.add(DefaultMutableTreeNode(ViewerTreeEntry(
                details,
                selectionKey = "script:${target.objectId}:$primId:${item.itemId}",
                pushTarget = target,
                scriptSelection = scriptSelection,
                icon = icon
            )))
        }
    }

    private fun appendLog(message: String) {
        connection.logActivity(message)
    }

    private fun updateActivityLog(entries: List<String>) {
        if (project.isDisposed || dialogDisposed) return
        val text = entries.joinToString("\n", postfix = if (entries.isEmpty()) "" else "\n")
        if (activityLog.text != text) {
            activityLog.text = text
            activityLog.caretPosition = activityLog.document.length
        }
    }

    private fun refreshActivityLog() {
        updateActivityLog(connection.getActivityLogSnapshot())
    }

}

private data class ViewerTreeEntry(
    val label: String,
    val selectionKey: String,
    val pushTarget: ViewerObject? = null,
    val pushAllTarget: ViewerObject? = null,
    val scriptSelection: ViewerScriptSelection? = null,
    val icon: ViewerTreeIcon,
    var projectMatch: ProjectScriptMatch? = null,
    var mappingResolved: Boolean = false
) {
    val hasProjectMatch: Boolean
        get() = mappingResolved && (projectMatch?.matchCount ?: 0) > 0

    override fun toString(): String = label
}

private data class ViewerPushRequest(
    val target: ViewerObject,
    val scripts: List<ViewerScriptSelection>?
)

private enum class ViewerTreeIcon {
    OBJECT,
    SCRIPT,
    ITEM
}

private class ViewerObjectTreeCellRenderer : DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        val component = super.getTreeCellRendererComponent(
            tree,
            value,
            selected,
            expanded,
            leaf,
            row,
            hasFocus
        )
        val node = value as? DefaultMutableTreeNode
        val entry = node?.userObject as? ViewerTreeEntry
        icon = when (entry?.icon) {
            ViewerTreeIcon.OBJECT -> OBJECT_ICON
            ViewerTreeIcon.SCRIPT -> {
                when {
                    !entry.mappingResolved || entry.projectMatch?.matchCount == 0 -> SCRIPT_DISABLED_ICON
                    (entry.projectMatch?.matchCount ?: 0) > 1 -> SCRIPT_WARNING_ICON
                    else -> SCRIPT_OK_ICON
                }
            }
            ViewerTreeIcon.ITEM -> AllIcons.FileTypes.Any_type
            null -> AllIcons.Nodes.Folder
        }
        toolTipText = when {
            entry?.icon != ViewerTreeIcon.SCRIPT -> null
            !entry.mappingResolved -> "Checking project for a matching script..."
            entry.projectMatch == null || entry.projectMatch?.matchCount == 0 ->
                "No matching .lsl build output or .luau file in the project."
            (entry.projectMatch?.matchCount ?: 0) > 1 ->
                "Multiple project matches (${entry.projectMatch?.matchCount}); updating ${entry.projectMatch?.relativePath}."
            else -> "Matches ${entry.projectMatch?.relativePath}"
        }
        return component
    }

    companion object {
        private val OBJECT_ICON = IconLoader.getIcon(
            "/icons/viewer-object.svg",
            ViewerObjectTreeCellRenderer::class.java
        )
        private val SCRIPT_OK_ICON = IconLoader.getIcon(
            "/icons/script-ok.svg",
            ViewerObjectTreeCellRenderer::class.java
        )
        private val SCRIPT_DISABLED_ICON = IconLoader.getIcon(
            "/icons/script-disabled.svg",
            ViewerObjectTreeCellRenderer::class.java
        )
        private val SCRIPT_WARNING_ICON = IconLoader.getIcon(
            "/icons/script-warning.svg",
            ViewerObjectTreeCellRenderer::class.java
        )
    }
}

private fun showNotification(project: Project, message: String, type: NotificationType) {
    if (project.isDisposed) return
    NotificationGroupManager.getInstance()
        .getNotificationGroup("LSL Viewer Integration")
        .createNotification("Second Life Viewer", message, type)
        .notify(project)
}

private fun connectionFailureMessage(error: Throwable): String {
    if (generateSequence(error) { it.cause }.any { it is ConnectException }) {
        return "Make sure the Second Life viewer is running and its External Editor Server is enabled on port ${LslSettings.instance.viewerWebSocketPort}."
    }
    return error.cause?.message ?: error.message ?: error.javaClass.simpleName
}
