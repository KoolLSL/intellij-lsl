package io.github.koollsl.lsl.viewer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.utils.LslFileTypeUtils
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
class LslProjectDeploymentService(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) {
    fun launchMappedDeployment(
        target: ViewerObject,
        modalityState: ModalityState,
        selectedScripts: List<ViewerScriptSelection>? = null,
        onComplete: () -> Unit
    ) = coroutineScope.launch {
        withContext(Dispatchers.IO) {
            val report = try {
                val latestTarget = project.service<LslViewerConnectionService>()
                    .refreshPublishedObjects()
                    .singleOrNull { it.objectId == target.objectId }
                    ?: error("The selected object is no longer published by the viewer.")
                if (selectedScripts == null) {
                    deployMappedScripts(latestTarget, modalityState)
                } else {
                    deploySelectedScripts(latestTarget, selectedScripts, modalityState)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                DeploymentReport(
                    target.objectName,
                    target.objectId,
                    listOf(
                        DeploymentLine(
                            "Push",
                            "Could not start push: ${error.message ?: error.javaClass.simpleName}",
                            showRunningState = false
                        )
                    )
                )
            }

            if (!project.isDisposed) {
                val connection = project.service<LslViewerConnectionService>()
                report.lines.forEach { line ->
                    line.warning?.let(connection::logActivity)
                    val entry = if (line.result == "OK.") {
                        "\u2713 ${line.scriptName}"
                    } else {
                        "\u2717 ${line.scriptName}: ${line.result}"
                    }
                    connection.logActivity(entry)
                }
                connection.logActivity("Push finished.")
                ApplicationManager.getApplication().invokeLater({
                    if (!project.isDisposed) onComplete()
                }, ModalityState.any())
            }
        }
    }

    fun deployMappedScripts(
        target: ViewerObject,
        modalityState: ModalityState
    ): DeploymentReport {
        val inventory = (
            target.inventory.map { TargetScript(target.objectId, it) } +
                target.linkedObjects.flatMap { linked ->
                    linked.inventory.map { item -> TargetScript(linked.linkId, item) }
                }
            ).filter { it.item.type.equals("script", ignoreCase = true) }
        val matches = findProjectScriptMatches(inventory.map { it.item.name })
        val lines = inventory.mapNotNull { targetScript ->
            val match = matches[targetScript.item.name.lowercase(Locale.ROOT)]
            if (match == null || match.matchCount == 0) return@mapNotNull null
            try {
                deployScript(targetScript, match, modalityState)
            } catch (error: Exception) {
                LOG.warn("Push failed for '${targetScript.item.name}' to ${target.objectId}", error)
                DeploymentLine(
                    targetScript.item.name,
                    "Failed: ${error.message ?: error.javaClass.simpleName}"
                )
            }
        }
        if (lines.isEmpty()) {
            return DeploymentReport(
                target.objectName,
                target.objectId,
                listOf(
                    DeploymentLine(
                        "-",
                        "No matching project scripts found; nothing was pushed.",
                        showRunningState = false
                    )
                )
            )
        }
        return DeploymentReport(target.objectName, target.objectId, lines)
    }

    private fun deploySelectedScripts(
        target: ViewerObject,
        selections: List<ViewerScriptSelection>,
        modalityState: ModalityState
    ): DeploymentReport {
        val inventory = (
            target.inventory.map { TargetScript(target.objectId, it) } +
                target.linkedObjects.flatMap { linked ->
                    linked.inventory.map { item -> TargetScript(linked.linkId, item) }
                }
            ).filter { it.item.type.equals("script", ignoreCase = true) }
        val uniqueSelections = selections.distinctBy { it.primId to it.itemId }
        val targetScripts = uniqueSelections.mapNotNull { selection ->
            inventory.singleOrNull {
                it.primId == selection.primId && it.item.itemId == selection.itemId
            }?.let { selection to it }
        }
        val matches = findProjectScriptMatches(targetScripts.map { it.first.scriptName })
        val targetScriptsByKey = targetScripts.associateBy { it.first.primId to it.first.itemId }
        val lines = uniqueSelections.map { selection ->
            val targetScript = targetScriptsByKey[selection.primId to selection.itemId]?.second
            if (targetScript == null) {
                DeploymentLine(selection.scriptName, "Script is no longer in the explored object.")
            } else {
                deployScript(targetScript, matches[selection.scriptName.lowercase(Locale.ROOT)], modalityState)
            }
        }
        return DeploymentReport(target.objectName, target.objectId, lines)
    }

    fun findProjectScriptMatches(scriptNames: Collection<String>): Map<String, ProjectScriptMatch> {
        val root = Path.of(checkNotNull(project.basePath) {
            "Open this project from a directory before matching viewer scripts."
        })
        check(Files.isDirectory(root)) { "Project root '$root' is not a directory." }
        val requested = scriptNames.map { it.lowercase(Locale.ROOT) }.toSet()
        if (requested.isEmpty()) return emptyMap()

        val candidates = mutableMapOf<String, MutableList<Path>>()
        Files.walk(root).use { files ->
            files.filter(Files::isRegularFile).forEach { path ->
                val fileName = path.fileName.toString()
                val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (extension != "lsl" && extension != "luau") return@forEach
                if (extension == "lsl" &&
                    path.parent.fileName.toString().equals("build", ignoreCase = true).not()
                ) return@forEach
                val baseName = fileName.substringBeforeLast('.').lowercase(Locale.ROOT)
                if (baseName in requested) candidates.getOrPut(baseName) { mutableListOf() }.add(path)
            }
        }

        return requested.associateWith { name ->
            val matches = candidates[name].orEmpty().sortedWith(
                compareBy<Path> { root.relativize(it).nameCount }
                    .thenBy { root.relativize(it).toString().lowercase(Locale.ROOT) }
            )
            matches.firstOrNull()?.let { selected ->
                ProjectScriptMatch(root.relativize(selected).toString(), matches.size)
            } ?: ProjectScriptMatch("", 0)
        }
    }

    private fun deployScript(
        targetScript: TargetScript,
        match: ProjectScriptMatch?,
        modalityState: ModalityState
    ): DeploymentLine {
        val scriptName = targetScript.item.name
        val warning = match?.takeIf { it.matchCount > 1 }?.let {
            "Warning: $scriptName has ${it.matchCount} matches in project; using ${it.relativePath}."
        }
        if (match == null || match.matchCount == 0) {
            return DeploymentLine(scriptName, "Not found in project.", targetScript.item.running, warning = warning)
        }
        val source = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(
            Path.of(checkNotNull(project.basePath), match.relativePath).toFile()
        ) ?: return DeploymentLine(
            scriptName,
            "Mapped project file is unavailable: ${match.relativePath}.",
            targetScript.item.running,
            warning = warning
        )
        return try {
            deployScriptToItem(scriptName, targetScript, source, modalityState, warning)
        } catch (error: Exception) {
            LOG.warn("Push failed for '$scriptName'", error)
            DeploymentLine(
                scriptName,
                "Failed: ${error.message ?: error.javaClass.simpleName}",
                targetScript.item.running,
                warning = warning
            )
        }
    }

    private fun deployScriptToItem(
        scriptName: String,
        targetItem: TargetScript,
        mappedFile: VirtualFile,
        modalityState: ModalityState,
        warning: String?
    ): DeploymentLine {
        val running = targetItem.item.running
        val isLuauSource = mappedFile.extension.equals("luau", ignoreCase = true)
        if (targetItem.item.subtype != null &&
            (targetItem.item.subtype == 1) != isLuauSource
        ) {
            return DeploymentLine(
                scriptName,
                "Project source and object script languages do not match.",
                running,
                warning = warning
            )
        }
        val output = buildOutput(mappedFile, modalityState)
            ?: return DeploymentLine(
                scriptName,
                "Build failed; no push output was produced.",
                running,
                warning = warning
            )
        val response = project.service<LslViewerConnectionService>().deployScript(
            targetItem.primId,
            targetItem.item.itemId,
            output.contentsToByteArray().toString(StandardCharsets.UTF_8),
            targetItem.item.vm
        )
        if (response.get("success")?.asBoolean != true) {
            return DeploymentLine(scriptName, "Viewer did not confirm the upload.", running, warning = warning)
        }
        if (response.get("compiled")?.asBoolean == false) {
            val diagnostics = response.getAsJsonArray("diagnostics")
                ?.mapNotNull { element ->
                    if (!element.isJsonObject) return@mapNotNull null
                    val diagnostic = element.asJsonObject
                    val message = readableDiagnosticText(diagnostic.get("message")) ?: return@mapNotNull null
                    val row = diagnostic.get("row")
                        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0
                    val column = diagnostic.get("column")
                        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0
                    when {
                        row > 0 && column > 0 -> "line $row, column $column: $message"
                        row > 0 -> "line $row: $message"
                        else -> message
                    }
                }
                .orEmpty()
            val legacyErrors = response.getAsJsonArray("errors")
                ?.mapNotNull(::readableDiagnosticText)
                .orEmpty()
            val details = (diagnostics + legacyErrors).distinct().joinToString("; ")
                .ifEmpty { readableDiagnosticText(response.get("message")).orEmpty() }
            return DeploymentLine(
                scriptName,
                if (details.isEmpty()) {
                    "Uploaded, but compilation failed (viewer returned an unreadable diagnostic)."
                } else {
                    "Uploaded, but compilation failed: $details"
                },
                running,
                warning = warning
            )
        }
        return DeploymentLine(scriptName, "OK.", running, warning = warning)
    }

    private fun readableDiagnosticText(element: com.google.gson.JsonElement?): String? {
        if (element == null || !element.isJsonPrimitive || !element.asJsonPrimitive.isString) return null
        val text = element.asString
        if (text.isBlank() || text.any {
                Character.isISOControl(it) && it !in listOf('\n', '\r', '\t')
            }
        ) return null
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun buildOutput(mappedFile: VirtualFile, modalityState: ModalityState): VirtualFile? {
        if (mappedFile.extension.equals("luau", ignoreCase = true)) return mappedFile
        if (!LslFileTypeUtils.isBuildFile(mappedFile)) return null

        val output = AtomicReference<VirtualFile?>()
        val buildDirectory = mappedFile.parent ?: return null
        val sourceDirectory = buildDirectory.parent ?: return null
        val source = listOf("lslp", "lsl")
            .mapNotNull { extension ->
                sourceDirectory.children.firstOrNull {
                    it.name.equals("${mappedFile.nameWithoutExtension}.$extension", ignoreCase = true)
                }
            }
            .firstOrNull()
        if (source == null) return mappedFile
        ApplicationManager.getApplication().invokeAndWait({
            WriteCommandAction.runWriteCommandAction(
                project,
                "Build Script for Viewer Push",
                null,
                Runnable {
                    output.set(project.service<LslPreprocessorEngine>().processFileOnSave(source))
                }
            )
        }, modalityState)
        return output.get()
    }

    private data class TargetScript(val primId: String, val item: ViewerInventoryItem)

    companion object {
        private val LOG = Logger.getInstance(LslProjectDeploymentService::class.java)
    }
}
