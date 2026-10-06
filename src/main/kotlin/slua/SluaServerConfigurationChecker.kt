package io.github.koollsl.lsl.slua

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import io.github.koollsl.lsl.shared.definitions.DefinitionsSourceManager
import java.io.File
import java.io.IOException

object SluaServerConfigurationChecker {
    private val serverEntry = Regex(
        """<UserDefinedLanguageServerItemSettings\b.*?</UserDefinedLanguageServerItemSettings>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val definitionArgument =
        Regex("""--definitions:@sl-slua=([^\s"<>&]+)""", RegexOption.IGNORE_CASE)
    private val docsArgument = Regex("""--docs=([^\s"<>&]+)""", RegexOption.IGNORE_CASE)

    fun hasOutdatedPaths(): Boolean {
        val settingsFile = File(
            PathManager.getConfigPath(),
            "options/UserDefinedLanguageServerSettings.xml"
        )
        if (!settingsFile.isFile) return false

        val configuration = try {
            settingsFile.readText()
        } catch (e: IOException) {
            LOG.warn("Could not inspect Luau language server settings", e)
            return false
        }

        val luauSettings = serverEntry.findAll(configuration)
            .map { it.value }
            .filter { it.contains("luau-lsp", ignoreCase = true) }
            .joinToString("\n")
        if (luauSettings.isEmpty()) return false

        val configuredDefinitions = definitionArgument.findAll(luauSettings)
            .map { normalizePath(it.groupValues[1]) }
            .toList()
        val configuredDocs = docsArgument.findAll(luauSettings)
            .map { normalizePath(it.groupValues[1]) }
            .toList()
        if (configuredDefinitions.isEmpty() || configuredDocs.isEmpty()) return false

        val expectedDefinitions =
            normalizePath(DefinitionsSourceManager.getLuauDefinitionsFile().absolutePath)
        val expectedDocs = normalizePath(DefinitionsSourceManager.getLuauDocsFile().absolutePath)
        return configuredDefinitions.any { it != expectedDefinitions } ||
            configuredDocs.any { it != expectedDocs }
    }

    private fun normalizePath(path: String): String =
        path.replace('\\', '/').trimEnd('/').lowercase()

    private val LOG = Logger.getInstance(SluaServerConfigurationChecker::class.java)
}
