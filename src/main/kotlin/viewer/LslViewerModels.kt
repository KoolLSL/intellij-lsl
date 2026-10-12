package io.github.koollsl.lsl.viewer

import com.google.gson.annotations.SerializedName

data class ViewerObject(
    @SerializedName("object_id") val objectId: String,
    @SerializedName("object_name") val objectName: String,
    val region: String? = null,
    val inventory: List<ViewerInventoryItem> = emptyList(),
    @SerializedName("linked_objects") val linkedObjects: List<ViewerLinkedObject> = emptyList()
)

data class ViewerLinkedObject(
    @SerializedName("link_id") val linkId: String,
    @SerializedName("link_name") val linkName: String,
    val inventory: List<ViewerInventoryItem> = emptyList()
)

data class ViewerInventoryItem(
    @SerializedName("item_id") val itemId: String,
    val name: String,
    val type: String,
    val subtype: Int? = null,
    val vm: String? = null,
    val running: Boolean? = null,
    val permissions: ViewerItemPermissions? = null
) {
    val nextOwnerPermissionFlags: String?
        get() = permissions?.nextOwner?.let { mask ->
            "${if (mask and PERM_COPY != 0L) 'C' else '-'}" +
                "${if (mask and PERM_MODIFY != 0L) 'M' else '-'}" +
                "${if (mask and PERM_TRANSFER != 0L) 'T' else '-'}"
        }

    private companion object {
        const val PERM_COPY = 0x00008000L
        const val PERM_MODIFY = 0x00004000L
        const val PERM_TRANSFER = 0x00002000L
    }
}

data class ViewerItemPermissions(
    @SerializedName("next_owner") val nextOwner: Long? = null
)

data class ViewerScriptSelection(
    val primId: String,
    val itemId: String,
    val scriptName: String
)

data class ProjectScriptMatch(
    val relativePath: String,
    val matchCount: Int
)

data class DeploymentLine(
    val scriptName: String,
    val result: String,
    val running: Boolean? = null,
    val showRunningState: Boolean = true,
    val warning: String? = null
)

data class DeploymentReport(
    val objectName: String,
    val objectId: String,
    val lines: List<DeploymentLine>
) {
    fun render(): String = buildString {
        appendLine("Object: $objectName ($objectId)")
        appendLine()
        lines.forEach { line ->
            append("${line.scriptName}: ${line.result}")
            if (line.showRunningState) {
                val state = when (line.running) {
                    true -> "Running"
                    false -> "Not running"
                    null -> "State unavailable"
                }
                append(" (Script: $state)")
            }
            appendLine()
        }
    }.trimEnd()

    fun renderHtml(): String = render()
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "<br>")
}
