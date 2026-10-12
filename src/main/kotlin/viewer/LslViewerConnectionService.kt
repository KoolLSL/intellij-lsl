package io.github.koollsl.lsl.viewer

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ModalityState
import io.github.koollsl.lsl.shared.settings.LslSettings
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Service(Service.Level.PROJECT)
class LslViewerConnectionService(private val project: Project) : WebSocket.Listener, Disposable {

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build()
    private val gson = com.google.gson.Gson()
    private val requestIds = AtomicLong()
    private val pendingRequests = ConcurrentHashMap<Long, CompletableFuture<JsonElement>>()
    private val publishedObjects = ConcurrentHashMap<String, ViewerObject>()
    private val connectionStateListeners = CopyOnWriteArrayList<() -> Unit>()
    private val publishedObjectsListeners = CopyOnWriteArrayList<(List<ViewerObject>) -> Unit>()
    private val activityLogLock = Any()
    private val activityLogEntries = ArrayDeque<String>()
    private val objectLock = Any()
    private val incomingText = StringBuilder()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var handshakeComplete: CompletableFuture<Unit>? = null
    @Volatile private var connecting = false

    val isConnecting: Boolean
        get() = connecting

    val isConnected: Boolean
        get() = socket != null && handshakeComplete?.isDone == true &&
            handshakeComplete?.isCompletedExceptionally == false

    fun addConnectionStateListener(listener: () -> Unit): () -> Unit {
        connectionStateListeners.add(listener)
        return { connectionStateListeners.remove(listener) }
    }

    fun addPublishedObjectsListener(listener: (List<ViewerObject>) -> Unit): () -> Unit {
        publishedObjectsListeners.add(listener)
        return { publishedObjectsListeners.remove(listener) }
    }

    fun getActivityLogSnapshot(): List<String> = synchronized(activityLogLock) {
        activityLogEntries.toList()
    }

    fun logActivity(message: String) {
        val entry = "[${LocalTime.now().format(LOG_TIME_FORMAT)}] $message"
        synchronized(activityLogLock) {
            activityLogEntries.addLast(entry)
            while (activityLogEntries.size > MAX_ACTIVITY_LOG_ENTRIES) {
                activityLogEntries.removeFirst()
            }
        }
        notifyPublishedObjectsChanged()
    }

    fun connect() {
        synchronized(this) {
            check(!connecting) { "A connection to the Second Life viewer is already in progress." }
            connecting = true
        }
        notifyConnectionStateChanged()
        try {
            connectAndHandshake()
        } finally {
            connecting = false
            notifyConnectionStateChanged()
        }
    }

    private fun connectAndHandshake() {
        check(LslSettings.instance.viewerIntegrationEnabled) {
            "Enable viewer push in LSL settings before connecting."
        }
        check(!isConnected) { "Already connected to the Second Life viewer." }

        val port = LslSettings.instance.viewerWebSocketPort
        require(port in 1..65535) { "Viewer WebSocket port must be between 1 and 65535." }
        val ready = CompletableFuture<Unit>()
        handshakeComplete = ready
        try {
            val webSocket = client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .buildAsync(URI("ws://localhost:$port"), this)
                .get(10, TimeUnit.SECONDS)
            socket = webSocket
            ready.get(35, TimeUnit.SECONDS)
        } catch (error: Exception) {
            disconnect()
            throw error
        }
    }

    fun disconnect() {
        val current = socket
        socket = null
        notifyConnectionStateChanged()
        handshakeComplete?.completeExceptionally(IllegalStateException("Disconnected"))
        handshakeComplete = null
        synchronized(objectLock) { publishedObjects.clear() }
        notifyPublishedObjectsChanged()
        pendingRequests.values.forEach {
            it.completeExceptionally(IllegalStateException("Disconnected from the viewer."))
        }
        pendingRequests.clear()
        if (current != null && !current.isOutputClosed) {
            try {
                current.sendText(
                    """{"jsonrpc":"2.0","method":"session.disconnect","params":{"reason":0,"message":"Disconnected by IntelliJ"}}""",
                    true
                ).whenComplete { _, _ ->
                    if (!current.isOutputClosed) {
                        current.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnected")
                    }
                }
            } catch (error: IllegalStateException) {
                LOG.debug("WebSocket was already closing during disconnect", error)
            }
        }
    }

    fun refreshPublishedObjects(): List<ViewerObject> {
        check(LslSettings.instance.viewerIntegrationEnabled) {
            "Enable viewer push in LSL settings before refreshing targets."
        }
        check(isConnected) { "Connect to the viewer before refreshing push targets." }
        val response = call("object.list", JsonObject())
        val objects = response.getAsJsonArray("objects")
            ?: error("The viewer did not return its published object list.")
        val refreshed = objects.map(::parsePublishedObject)
        synchronized(objectLock) {
            publishedObjects.clear()
            refreshed.forEach { publishedObjects[it.objectId] = it }
        }
        val sorted = refreshed.sortedWith(objectComparator)
        notifyPublishedObjectsChanged()
        return sorted
    }

    fun getPublishedObjects(): List<ViewerObject> = synchronized(objectLock) {
        publishedObjects.values.sortedWith(objectComparator)
    }

    fun deployScript(
        primId: String,
        itemId: String,
        content: String,
        vm: String?
    ): JsonObject {
        check(LslSettings.instance.viewerIntegrationEnabled) {
            "Enable viewer push in LSL settings before pushing scripts."
        }
        check(isConnected) { "The viewer connection is no longer active." }
        val params = JsonObject().apply {
            addProperty("prim_id", primId)
            addProperty("item_id", itemId)
            addProperty("content", content)
            vm?.let { addProperty("vm", it) }
            // Omitting running leaves the script's existing run state unchanged
        }
        return call("object.content.save", params, timeoutSeconds = 70)
    }

    private fun call(method: String, params: JsonObject, timeoutSeconds: Long = 30): JsonObject {
        val current = checkNotNull(socket) { "Not connected to the viewer." }
        val id = requestIds.incrementAndGet()
        val response = CompletableFuture<JsonElement>()
        pendingRequests[id] = response
        val message = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", id)
            addProperty("method", method)
            add("params", params)
        }
        try {
            current.sendText(gson.toJson(message), true).get(5, TimeUnit.SECONDS)
            val result = try {
                response.get(timeoutSeconds, TimeUnit.SECONDS)
            } catch (error: ExecutionException) {
                throw IllegalStateException(error.cause?.message ?: "Viewer request failed.", error.cause)
            }
            check(result.isJsonObject) { "Viewer returned an invalid response to $method." }
            return result.asJsonObject
        } finally {
            pendingRequests.remove(id)
        }
    }

    override fun onOpen(webSocket: WebSocket) {
        socket = webSocket
        webSocket.request(1)
    }

    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletableFuture<*> {
        synchronized(incomingText) {
            incomingText.append(data)
            if (last) {
                val message = incomingText.toString()
                incomingText.setLength(0)
                handleMessage(webSocket, message)
            }
        }
        webSocket.request(1)
        return CompletableFuture.completedFuture(null)
    }

    override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletableFuture<*> {
        if (socket !== webSocket) return CompletableFuture.completedFuture(null)
        socket = null
        notifyConnectionStateChanged()
        synchronized(objectLock) { publishedObjects.clear() }
        notifyPublishedObjectsChanged()
        val failure = IllegalStateException("Viewer connection closed: $reason ($statusCode)")
        pendingRequests.values.forEach { it.completeExceptionally(failure) }
        handshakeComplete?.completeExceptionally(failure)
        return CompletableFuture.completedFuture(null)
    }

    override fun onError(webSocket: WebSocket, error: Throwable) {
        if (socket !== webSocket) return
        socket = null
        notifyConnectionStateChanged()
        synchronized(objectLock) { publishedObjects.clear() }
        notifyPublishedObjectsChanged()
        pendingRequests.values.forEach { it.completeExceptionally(error) }
        handshakeComplete?.completeExceptionally(error)
        LOG.warn("Viewer WebSocket failed", error)
    }

    private fun handleMessage(webSocket: WebSocket, text: String) {
        try {
            val message = JsonParser.parseString(text).asJsonObject
            if (message.has("id") && message.has("method")) {
                handleHandshake(webSocket, message)
                return
            }
            if (message.has("id")) {
                val id = message.get("id").asLong
                val waiting = pendingRequests[id] ?: return
                if (message.has("error")) {
                    waiting.completeExceptionally(
                        IllegalStateException(message.getAsJsonObject("error").get("message").asString)
                    )
                } else {
                    waiting.complete(message.get("result"))
                }
                return
            }
            when (message.get("method")?.asString) {
                "session.ok" -> {
                    handshakeComplete?.complete(Unit)
                    notifyConnectionStateChanged()
                    ApplicationManager.getApplication().executeOnPooledThread {
                        try {
                            refreshPublishedObjects()
                        } catch (error: Exception) {
                            LOG.warn("Could not load viewer push targets", error)
                            notify(
                                "Could not load viewer push targets",
                                error.message ?: error.javaClass.simpleName,
                                NotificationType.WARNING
                            )
                        }
                    }
                }
                "session.disconnect" -> socket?.sendClose(WebSocket.NORMAL_CLOSURE, "Viewer disconnected")
                "object.publish" -> {
                    val obj = message.getAsJsonObject("params")?.get("object")
                        ?: error("Viewer published an object without its object data.")
                    val published = parsePublishedObject(obj)
                    synchronized(objectLock) { publishedObjects[published.objectId] = published }
                    notifyPublishedObjectsChanged()
                }
                "object.unpublish" -> {
                    val objectId = message.getAsJsonObject("params")?.get("object_id")?.asString
                    if (objectId != null) {
                        synchronized(objectLock) { publishedObjects.remove(objectId) }
                        notifyPublishedObjectsChanged()
                    }
                }
                "object.update" -> updatePublishedObject(message.getAsJsonObject("params"))
                "script.compiled" -> handleCompilation(message.getAsJsonObject("params"))
            }
        } catch (error: Exception) {
            LOG.warn("Could not process viewer WebSocket message", error)
            handshakeComplete?.completeExceptionally(error)
        }
    }

    private fun updatePublishedObject(update: JsonObject?) {
        if (update == null) return
        val objectId = update.get("object_id")?.asString ?: return
        synchronized(objectLock) {
            val current = publishedObjects[objectId] ?: return
            val inventory = update.getAsJsonArray("inventory")
                ?.let(::parseInventory)
                ?: current.inventory
            val linkedObjects = update.getAsJsonArray("linked_objects")
                ?.let(::parseLinkedObjects)
                ?: update.getAsJsonObject("changes")
                    ?.getAsJsonObject("linked_objects")
                    ?.getAsJsonArray("modified")
                    ?.let { applyLinkedObjectChanges(current.linkedObjects, it) }
                ?: current.linkedObjects
            publishedObjects[objectId] = current.copy(
                objectName = update.get("object_name")?.asString ?: current.objectName,
                region = update.get("region")?.asString ?: current.region,
                inventory = inventory,
                linkedObjects = linkedObjects
            )
        }
        notifyPublishedObjectsChanged()
    }

    private fun applyLinkedObjectChanges(
        current: List<ViewerLinkedObject>,
        changes: JsonArray
    ): List<ViewerLinkedObject> {
        val updates = changes.associate { change ->
            val json = change.asJsonObject
            json.get("link_id").asString to json
        }
        return current.map { linked ->
            val update = updates[linked.linkId] ?: return@map linked
            linked.copy(
                linkName = update.get("link_name")?.asString ?: linked.linkName,
                inventory = update.getAsJsonArray("inventory")?.let(::parseInventory) ?: linked.inventory
            )
        }
    }

    private fun parsePublishedObject(element: JsonElement): ViewerObject {
        val published = gson.fromJson(element, ViewerObject::class.java)
        check(published.objectId.isNotBlank() && published.objectName.isNotBlank()) {
            "Viewer returned an object without an id or name."
        }
        return published.copy(
            inventory = published.inventory.orEmpty(),
            linkedObjects = published.linkedObjects.orEmpty().map { linked ->
                linked.copy(inventory = linked.inventory.orEmpty())
            }
        )
    }

    private fun parseInventory(items: JsonArray): List<ViewerInventoryItem> =
        items.map { gson.fromJson(it, ViewerInventoryItem::class.java) }

    private fun parseLinkedObjects(items: JsonArray): List<ViewerLinkedObject> =
        items.map { gson.fromJson(it, ViewerLinkedObject::class.java) }
            .map { linked -> linked.copy(inventory = linked.inventory.orEmpty()) }

    private fun handleHandshake(webSocket: WebSocket, request: JsonObject) {
        check(request.get("method").asString == "session.handshake") {
            "Viewer sent an unsupported request before completing the handshake."
        }
        val params = request.getAsJsonObject("params")
        val result = JsonObject().apply {
            addProperty("client_name", "KoolLSL")
            addProperty("client_version", "1.0")
            addProperty("protocol_version", "1.0")
            add("languages", gson.toJsonTree(listOf("lsl", "luau")))
            add("features", JsonObject().apply {
                addProperty("object_publish", true)
            })
            params.get("challenge")?.takeIf { !it.isJsonNull }?.let {
                addProperty("challenge_response", Files.readString(Path.of(it.asString)).trim())
            }
        }
        val response = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            add("id", request.get("id"))
            add("result", result)
        }
        webSocket.sendText(gson.toJson(response), true)
    }

    private fun handleCompilation(params: JsonObject?) {
        val success = params?.get("success")?.asBoolean == true
        if (success) return
        val diagnostics = params?.getAsJsonArray("diagnostics")
        val details = diagnostics?.mapNotNull { it.asJsonObject.get("message")?.asString }
            ?.joinToString("\n")
            .orEmpty()
        notify(
            "Viewer compilation failed",
            details.ifEmpty { params?.get("script_id")?.asString ?: "Viewer returned a compile result." },
            NotificationType.ERROR
        )
    }

    private fun notify(title: String, content: String, type: NotificationType) {
        if (project.isDisposed) return
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LSL Viewer Integration")
            .createNotification(title, content, type)
            .notify(project)
    }

    private fun notifyConnectionStateChanged() {
        if (project.isDisposed) return
        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) {
                connectionStateListeners.forEach { it() }
            }
        }, ModalityState.any())
    }

    private fun notifyPublishedObjectsChanged() {
        if (project.isDisposed) return
        val snapshot = getPublishedObjects()
        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) publishedObjectsListeners.forEach { it(snapshot) }
        }, ModalityState.any())
    }

    override fun dispose() {
        disconnect()
    }

    companion object {
        private val LOG = Logger.getInstance(LslViewerConnectionService::class.java)
        private val LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
        private const val MAX_ACTIVITY_LOG_ENTRIES = 500
        private val objectComparator = compareBy<ViewerObject> { it.objectName.lowercase() }
            .thenBy { it.objectId }
    }
}
