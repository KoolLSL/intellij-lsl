package io.github.koollsl.lsl.shared.definitions

import com.intellij.openapi.application.PathManager
import io.github.koollsl.lsl.shared.settings.LslSettings
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties

/**
 * Resolves bundled or cached Second Life definitions and periodically refreshes them from GitHub.
 */
object DefinitionsSourceManager {
    private const val CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
    private const val LAST_CHECK_KEY = "lastCheckEpochMillis"
    private const val LAST_UPDATE_KEY = "lastUpdateEpochMillis"
    private const val SOURCE_KEY_PREFIX = "source."
    private const val BUNDLED_SOURCE = "Bundled"
    private const val GITHUB_SOURCE = "GitHub cache"

    private val lock = Any()

    private data class DefinitionFile(
        val name: String,
        val resourceName: String,
        val url: String,
        val validate: (ByteArray) -> Boolean
    )

    private data class DownloadResult(
        val bytes: ByteArray?,
        val etag: String?
    )

    data class UpdateResult(
        val checked: Boolean,
        val updatedFiles: Set<String> = emptySet(),
        val failures: Map<String, String> = emptyMap()
    )

    data class UpdateStatus(
        val lastCheck: Instant?,
        val lastUpdate: Instant?
    )

    data class DefinitionInfo(
        val filename: String,
        val datetime: String,
        val source: String
    )

    private val fileDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private val lslDefinitions = DefinitionFile(
        name = "lsl_definitions.yaml",
        resourceName = "lsl_definitions.yaml",
        url = "https://raw.githubusercontent.com/secondlife/lsl-definitions/main/lsl_definitions.yaml",
        validate = { it.toString(Charsets.UTF_8).isNotBlank() }
    )

    private val luauDefinitions = DefinitionFile(
        name = "secondlife.d.luau",
        resourceName = "secondlife.d.luau",
        url = "https://raw.githubusercontent.com/secondlife/lsl-definitions/main/generated/secondlife.d.luau",
        validate = { it.toString(Charsets.UTF_8).isNotBlank() }
    )

    private val luauDocs = DefinitionFile(
        name = "secondlife.docs.json",
        resourceName = "secondlife.docs.json",
        url = "https://raw.githubusercontent.com/secondlife/lsl-definitions/main/generated/secondlife.docs.json",
        validate = {
            val content = it.toString(Charsets.UTF_8).trimStart()
            content.startsWith("{") || content.startsWith("[")
        }
    )

    private val cacheDirectory: File
        get() = File(PathManager.getSystemPath(), "lsl-intellij/definitions")

    private val metadataFile: File
        get() = File(cacheDirectory, "update.properties")

    /**
     * Checks GitHub at most once per day. Downloads each usable file to a temporary file before
     * replacing the cached copy. LSL and SLua updates are independent; the two SLua artifacts are
     * only replaced when both downloads succeed.
     */
    fun updateFromGithubIfDue(force: Boolean = false): UpdateResult = synchronized(lock) {
        val now = System.currentTimeMillis()
        val metadata = loadMetadata()
        val lastCheck = metadata.getProperty(LAST_CHECK_KEY)?.toLongOrNull()

        if (!force && lastCheck != null && now - lastCheck < CHECK_INTERVAL_MILLIS) {
            return@synchronized UpdateResult(checked = false)
        }

        ensureCacheDirectory()
        val updated = mutableSetOf<String>()
        val failures = mutableMapOf<String, String>()
        var changed = false

        val lslResult = download(lslDefinitions, metadata, failures)
        if (lslResult != null) {
            if (lslResult.bytes != null) {
                replaceFile(lslDefinitions, lslResult.bytes)
                changed = true
                updated.add(lslDefinitions.name)
            }
            metadata.setProperty(sourceKey(lslDefinitions), GITHUB_SOURCE)
            saveEtag(metadata, lslDefinitions, lslResult.etag)
        }

        val luauDownloads = listOf(luauDefinitions, luauDocs).mapNotNull { definition ->
            download(definition, metadata, failures)?.let { definition to it }
        }
        val luauDownloadsComplete = luauDownloads.size == 2 &&
            luauDefinitions.name !in failures &&
            luauDocs.name !in failures

        if (luauDownloadsComplete) {
            luauDownloads.forEach { (definition, result) ->
                if (result.bytes != null) {
                    replaceFile(definition, result.bytes)
                    changed = true
                    updated.add(definition.name)
                }
                metadata.setProperty(sourceKey(definition), GITHUB_SOURCE)
                saveEtag(metadata, definition, result.etag)
            }
        }

        metadata.setProperty(LAST_CHECK_KEY, now.toString())
        if (changed) {
            metadata.setProperty(LAST_UPDATE_KEY, now.toString())
        }
        saveMetadata(metadata)

        UpdateResult(checked = true, updatedFiles = updated, failures = failures)
    }

    /**
     * Returns the newest cached LSL YAML, or the bundled copy if no downloaded copy is available.
     */
    fun openLslDefinitions(): InputStream {
        customFile(lslDefinitions)?.let { return it.inputStream() }
        synchronized(lock) {
            val cached = cachedFile(lslDefinitions)
            if (!LslSettings.instance.useCustomDefinitionsFolder && cached.isFile) {
                return cached.inputStream()
            }
        }
        return openBundledResource(lslDefinitions)
    }

    /**
     * Returns the newest cached Luau definition file, materializing the bundled copy if needed.
     */
    fun getLuauDefinitionsFile(): File = resolveSelectedFile(luauDefinitions)

    /**
     * Returns the newest cached Luau documentation file, materializing the bundled copy if needed.
     */
    fun getLuauDocsFile(): File = resolveSelectedFile(luauDocs)

    fun getLuauDefinitionsSourceDescription(): String = getSourceDescription(luauDefinitions)

    fun getLuauDocsSourceDescription(): String = getSourceDescription(luauDocs)

    fun getLslDefinitionsInfo(): DefinitionInfo = getDefinitionInfo(lslDefinitions)

    fun getLuauDefinitionsInfo(): DefinitionInfo = getDefinitionInfo(luauDefinitions)

    fun getLuauDocsInfo(): DefinitionInfo = getDefinitionInfo(luauDocs)

    fun getUpdateStatus(): UpdateStatus = synchronized(lock) {
        val metadata = loadMetadata()
        UpdateStatus(
            lastCheck = metadata.getProperty(LAST_CHECK_KEY)?.toLongOrNull()?.let(Instant::ofEpochMilli),
            lastUpdate = metadata.getProperty(LAST_UPDATE_KEY)?.toLongOrNull()?.let(Instant::ofEpochMilli)
        )
    }

    fun getLastCheckDescription(): String = try {
        getUpdateStatus().lastCheck
            ?.atZone(ZoneId.systemDefault())
            ?.format(fileDateTimeFormatter)
            ?: "Never"
    } catch (_: Exception) {
        "unknown"
    }

    fun getLslSourceDescription(): String {
        return getSourceDescription(lslDefinitions)
    }

    private fun getSourceDescription(definition: DefinitionFile): String {
        val settings = LslSettings.instance
        if (customFile(definition) != null) return "Custom folder"

        synchronized(lock) {
            val cached = cachedFile(definition)
            if (!settings.useCustomDefinitionsFolder && cached.isFile) {
                val metadata = loadMetadata()
                return metadata.getProperty(sourceKey(definition), GITHUB_SOURCE)
            }
        }
        return BUNDLED_SOURCE
    }

    fun getLslDefinitionsLastModified(): Instant? {
        return getSelectedLastModified(lslDefinitions)
    }

    private fun getDefinitionInfo(definition: DefinitionFile): DefinitionInfo {
        val datetime = try {
            getSelectedLastModified(definition)
                ?.atZone(ZoneId.systemDefault())
                ?.format(fileDateTimeFormatter)
                ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
        val source = try {
            getSourceDescription(definition)
        } catch (_: Exception) {
            "unknown"
        }
        return DefinitionInfo(definition.name, datetime, source)
    }

    private fun download(
        definition: DefinitionFile,
        metadata: Properties,
        failures: MutableMap<String, String>
    ): DownloadResult? {
        val cached = cachedFile(definition)
        val connection = URL(definition.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.requestMethod = "GET"

        if (cached.isFile) {
            metadata.getProperty(etagKey(definition))?.let {
                connection.setRequestProperty("If-None-Match", it)
            }
        }

        return try {
            val responseCode = connection.responseCode
            when {
                responseCode == HttpURLConnection.HTTP_NOT_MODIFIED && cached.isFile ->
                    DownloadResult(bytes = null, etag = connection.getHeaderField("ETag"))

                responseCode == HttpURLConnection.HTTP_OK -> {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    if (!definition.validate(bytes)) {
                        failures[definition.name] = "Downloaded file failed validation."
                        null
                    } else {
                        val changedBytes = if (cached.isFile && cached.readBytes().contentEquals(bytes)) {
                            null
                        } else {
                            bytes
                        }
                        DownloadResult(bytes = changedBytes, etag = connection.getHeaderField("ETag"))
                    }
                }

                else -> {
                    failures[definition.name] = "GitHub returned HTTP $responseCode."
                    null
                }
            }
        } catch (e: IOException) {
            failures[definition.name] = e.message ?: e.javaClass.simpleName
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveSelectedFile(definition: DefinitionFile): File {
        customFile(definition)?.let { return it }
        return synchronized(lock) {
            val cached = cachedFile(definition)
            if (!LslSettings.instance.useCustomDefinitionsFolder && cached.isFile) return@synchronized cached

            ensureCacheDirectory()
            val bytes = openBundledResource(definition).use { it.readBytes() }
            if (!definition.validate(bytes)) {
                throw IOException("Bundled ${definition.resourceName} failed validation.")
            }
            replaceFile(definition, bytes)
            val metadata = loadMetadata()
            metadata.setProperty(sourceKey(definition), BUNDLED_SOURCE)
            saveMetadata(metadata)
            cached
        }
    }

    private fun getSelectedLastModified(definition: DefinitionFile): Instant? {
        customFile(definition)?.let { file ->
            return Files.getLastModifiedTime(file.toPath()).toInstant()
        }

        synchronized(lock) {
            val cached = cachedFile(definition)
            if (!LslSettings.instance.useCustomDefinitionsFolder && cached.isFile) {
                return Files.getLastModifiedTime(cached.toPath()).toInstant()
            }
        }

        val resourceUrl = javaClass.classLoader.getResource(definition.resourceName)
            ?: throw IOException("Bundled ${definition.resourceName} resource was not found.")
        return resourceUrl.openConnection().lastModified
            .takeIf { it > 0L }
            ?.let(Instant::ofEpochMilli)
    }

    private fun customFile(definition: DefinitionFile): File? {
        val folder = LslSettings.instance.customDefinitionsFolder.takeIf {
            LslSettings.instance.useCustomDefinitionsFolder && it.isNotBlank()
        }?.let(::File) ?: return null

        if (definition == luauDefinitions || definition == luauDocs) {
            val (luauFile, docsFile) = customLuauFiles(folder) ?: return null
            return if (definition == luauDefinitions) luauFile else docsFile
        }

        return findCustomFile(folder, definition, listOf(definition.name))
    }

    private fun customLuauFiles(folder: File): Pair<File, File>? {
        val definitionFile = findCustomFile(
            folder,
            luauDefinitions,
            listOf(luauDefinitions.name, "generated/${luauDefinitions.name}")
        ) ?: return null
        val docsFile = findCustomFile(
            folder,
            luauDocs,
            listOf(luauDocs.name, "generated/${luauDocs.name}")
        ) ?: return null
        return definitionFile to docsFile
    }

    private fun findCustomFile(
        folder: File,
        definition: DefinitionFile,
        relativePaths: List<String>
    ): File? =
        relativePaths.asSequence()
            .map { File(folder, it) }
            .firstOrNull { file -> file.isFile && definition.validate(file.readBytes()) }

    private fun openBundledResource(definition: DefinitionFile): InputStream =
        javaClass.classLoader.getResourceAsStream(definition.resourceName)
            ?: throw IOException("Bundled ${definition.resourceName} resource was not found.")

    private fun cachedFile(definition: DefinitionFile): File =
        File(cacheDirectory, definition.name)

    private fun ensureCacheDirectory() {
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            throw IOException("Could not create definitions cache directory: $cacheDirectory")
        }
    }

    private fun replaceFile(definition: DefinitionFile, bytes: ByteArray) {
        ensureCacheDirectory()
        val destination = cachedFile(definition).toPath()
        val temporary = Files.createTempFile(cacheDirectory.toPath(), "${definition.name}.", ".tmp")
        try {
            Files.write(temporary, bytes)
            try {
                Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun loadMetadata(): Properties {
        val properties = Properties()
        if (metadataFile.isFile) {
            metadataFile.inputStream().use(properties::load)
        }
        return properties
    }

    private fun saveMetadata(metadata: Properties) {
        ensureCacheDirectory()
        val temporary = Files.createTempFile(cacheDirectory.toPath(), "update.", ".tmp")
        try {
            Files.newOutputStream(temporary).use {
                metadata.store(it, "Second Life definitions update status")
            }
            try {
                Files.move(
                    temporary,
                    metadataFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, metadataFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun saveEtag(metadata: Properties, definition: DefinitionFile, etag: String?) {
        if (etag != null) {
            metadata.setProperty(etagKey(definition), etag)
        }
    }

    private fun etagKey(definition: DefinitionFile): String = "etag.${definition.name}"

    private fun sourceKey(definition: DefinitionFile): String = "$SOURCE_KEY_PREFIX${definition.name}"
}
