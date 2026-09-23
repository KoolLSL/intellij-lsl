package io.github.koollsl.lsl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.util.XmlUtil
import io.github.koollsl.lsl.psi.*
import io.github.koollsl.lsl.settings.LslSettings
import java.nio.file.Path

class KwdbData(val project: Project) {
    val data: XmlFile
    val lang = "en"
    val generated: LslFile

    var kwdbSourceInfo: String = ""
        private set

    val functions: Map<String, LslFunction>
    val constants: Map<String, LslGlobalVariable>
    val events: Map<String, LslEvent>

    companion object {
        val KWDB_DATA_KEY = Key.create<KwdbData>("KWDB_DATA")

        fun getInstance(project: Project): KwdbData = project.getUserData(KWDB_DATA_KEY) ?: let {
            val data = KwdbData(project)
            project.putUserData(KWDB_DATA_KEY, data)
            data
        }
    }

    init {
        val xmlVirtualFile: VirtualFile = findCustomOrResourceKwdb()
        data = PsiManager.getInstance(project).findFile(xmlVirtualFile) as XmlFile
        generated = LslElementFactory.createFile(project, generateSource())

        functions = generated.children.filterIsInstance<LslFunction>().associateBy { it.name!! }
        constants = generated.children.filterIsInstance<LslGlobalVariable>().associateBy { it.name!! }
        events = PsiTreeUtil.collectElementsOfType(generated, LslEvent::class.java).associateBy { it.name!! }
    }

    private fun findCustomOrResourceKwdb(): VirtualFile {
        fun extractKwdbVersion(xmlFile: XmlFile): String {
            val root = xmlFile.rootTag ?: return "unknown"
            return if (root.name == "llsd") {
                "LLSD v0.7+"
            } else {
                root.getAttributeValue("version") ?: "unknown"
            }
        }

        val customPathStr = LslSettings.instance.customKwdbPath

        if (customPathStr.isNotBlank()) {
            val customPath = Path.of(customPathStr)
            val customFile = customPath.toFile()
            if (customFile.exists()) {
                val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(customFile)
                if (vFile != null && vFile.isValid) {
                    val xmlPsi = PsiManager.getInstance(project).findFile(vFile) as? XmlFile
                    val version = xmlPsi?.let { extractKwdbVersion(it) } ?: "unknown"

                    kwdbSourceInfo = "Custom (${customFile.name}, format: $version)"
                    return vFile
                }
            }
        }

        // Integrated fallback
        val resourceUrl = javaClass.classLoader.getResource("kwdb.xml")
            ?: throw IllegalStateException("Bundled kwdb.xml missing from plugin JAR resources")

        val integratedVFile = VfsUtil.findFileByURL(resourceUrl)
            ?: throw IllegalStateException("Could not resolve VirtualFile for bundled kwdb.xml")

        val xmlPsi = PsiManager.getInstance(project).findFile(integratedVFile) as? XmlFile
        val version = xmlPsi?.let { extractKwdbVersion(it) } ?: "unknown"

        kwdbSourceInfo = "Integrated kwdb.xml ($version)"
        return integratedVFile
    }

    fun getByName(name: String?): LslNamedElement? =
        functions[name] ?: constants[name]

    fun hasElement(element: PsiElement): Boolean =
        PsiTreeUtil.isAncestor(generated, element, true)

    fun commentDescription(description: String): String =
        description.trim().split('\n').filter { it != "<!-- TODO: add documentation -->" }
            .joinToString("\n") { "// $it" }

    fun isSLGrid(tag: XmlTag): Boolean = (tag.getAttributeValue("grid") ?: "sl").split(' ').contains("sl")

    /**
     * Entry point: Decides format based on root tag and delegates generation.
     */
    fun generateSource(): String {
        val root = data.rootTag ?: return ""
        return if (root.name == "llsd") {
            generateLlsdSource(root)
        } else {
            generateLegacySource(root)
        }
    }

    // ==========================================
    // 1. LEGACY FORMAT GENERATOR (kwdb.xml)
    // ==========================================
    private fun generateLegacySource(root: XmlTag): String {
        val sb = StringBuilder()
        val subtags = root.subTags.filter { isSLGrid(it) }
        subtags.forEach { tag ->
            when (tag.name) {
                "constant" -> {
                    tag.findSubTags("description").forEach { description ->
                        sb.append("${commentDescription(description.value.text)}\n")
                    }

                    val type = tag.getAttributeValue("type")
                    val value = when (type) {
                        "string", "key" -> "\"${tag.getAttributeValue("value")
                            ?.replace(Regex("""\\x([0-9a-fA-F]{2})""")) {
                                it.groupValues[1].toInt(16).toChar().toString()
                            } ?: ""
                        }\""

                        "vector", "rotation", "quaternion" -> XmlUtil.unescape(tag.getAttributeValue("value") ?: "")
                        else -> tag.getAttributeValue("value")
                    }

                    sb.append("$type ${tag.getAttributeValue("name")} = $value;\n")
                }

                "function" -> {
                    tag.findSubTags("description").forEach { description ->
                        sb.append("${commentDescription(description.value.text)}\n")
                    }

                    val type = tag.getAttributeValue("type")
                    if (type != null) {
                        sb.append("$type ")
                    }
                    sb.append("${tag.getAttributeValue("name")}(")
                    tag.findSubTags("param").forEachIndexed { index, param ->
                        if (index != 0) sb.append(", ")
                        sb.append("${param.getAttributeValue("type")} ${param.getAttributeValue("name")}")
                    }
                    sb.append(") {}\n")
                }
            }
        }

        sb.append("default {\n")
        subtags.filter { it.name == "event" }.forEach { tag ->
            tag.findSubTags("description").forEach { description ->
                sb.append("    ${commentDescription(description.value.text)}\n")
            }

            sb.append("    ${tag.getAttributeValue("name")} (")
            tag.findSubTags("param").forEachIndexed { index, param ->
                if (index != 0) sb.append(", ")
                sb.append("${param.getAttributeValue("type")} ${param.getAttributeValue("name")}")
            }
            sb.append(") {}\n")
        }
        sb.append("}\n")

        return sb.toString()
    }

    // ==========================================
    // 2. NEW LLSD FORMAT GENERATOR (lsl_keywords.xml)
    // ==========================================
    private fun generateLlsdSource(root: XmlTag): String {
        val sb = StringBuilder()

        // Root LLSD contains a single top-level <map>
        val rootMapTag = root.findFirstSubTag("map") ?: return ""
        val llsdData = rootMapTag.toLlsdMap()

        // Extract Constants
        val constantsMap = llsdData["constants"] as? Map<String, Map<String, Any?>> ?: emptyMap()
        for ((name, entry) in constantsMap) {
            val tooltip = entry["tooltip"] as? String
            if (!tooltip.isNullOrBlank()) {
                sb.append("${commentDescription(tooltip)}\n")
            }

            val type = entry["type"] as? String ?: "integer"
            val rawValue = entry["value"]?.toString() ?: "0"
            val value = when (type) {
                "string", "key" -> "\"$rawValue\""
                else -> rawValue
            }

            sb.append("$type $name = $value;\n")
        }

        // Extract Functions
        val functionsMap = llsdData["functions"] as? Map<String, Map<String, Any?>> ?: emptyMap()
        for ((name, entry) in functionsMap) {
            val tooltip = entry["tooltip"] as? String
            if (!tooltip.isNullOrBlank()) {
                sb.append("${commentDescription(tooltip)}\n")
            }

            val returnType = entry["return"] as? String
            if (!returnType.isNullOrBlank() && returnType != "void") {
                sb.append("$returnType ")
            }

            sb.append("$name(")

            @Suppress("UNCHECKED_CAST")
            val argsList = entry["arguments"] as? List<Map<String, Map<String, Any?>>> ?: emptyList()
            argsList.forEachIndexed { index, argMap ->
                val paramName = argMap.keys.firstOrNull() ?: return@forEachIndexed
                val paramProps = argMap[paramName] ?: emptyMap()
                val paramType = paramProps["type"] as? String ?: "void"

                if (index != 0) sb.append(", ")
                sb.append("$paramType $paramName")
            }
            sb.append(") {}\n")
        }

        // Extract Events inside default block
        val eventsMap = llsdData["events"] as? Map<String, Map<String, Any?>> ?: emptyMap()
        sb.append("default {\n")
        for ((name, entry) in eventsMap) {
            val tooltip = entry["tooltip"] as? String
            if (!tooltip.isNullOrBlank()) {
                sb.append("    ${commentDescription(tooltip)}\n")
            }

            sb.append("    $name(")

            @Suppress("UNCHECKED_CAST")
            val argsList = entry["arguments"] as? List<Map<String, Map<String, Any?>>> ?: emptyList()
            argsList.forEachIndexed { index, argMap ->
                val paramName = argMap.keys.firstOrNull() ?: return@forEachIndexed
                val paramProps = argMap[paramName] ?: emptyMap()
                val paramType = paramProps["type"] as? String ?: "void"

                if (index != 0) sb.append(", ")
                sb.append("$paramType $paramName")
            }
            sb.append(") {}\n")
        }
        sb.append("}\n")

        return sb.toString()
    }

    // ==========================================
    // 3. LLSD XML PARSER HELPERS
    // ==========================================
    private fun XmlTag.toLlsdMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val tags = subTags
        var currentKey: String? = null

        for (tag in tags) {
            when (tag.name) {
                "key" -> currentKey = tag.value.trimmedText
                "string" -> currentKey?.let { map[it] = tag.value.trimmedText }
                "integer" -> currentKey?.let { map[it] = tag.value.trimmedText.toIntOrNull() ?: tag.value.trimmedText }
                "real" -> currentKey?.let { map[it] = tag.value.trimmedText.toDoubleOrNull() ?: tag.value.trimmedText }
                "boolean" -> currentKey?.let { map[it] = tag.value.trimmedText.toBoolean() }
                "map" -> currentKey?.let { map[it] = tag.toLlsdMap() }
                "array" -> currentKey?.let { map[it] = tag.toLlsdList() }
            }
        }
        return map
    }

    private fun XmlTag.toLlsdList(): List<Any?> {
        val list = mutableListOf<Any?>()
        for (tag in subTags) {
            when (tag.name) {
                "string" -> list.add(tag.value.trimmedText)
                "integer" -> list.add(tag.value.trimmedText.toIntOrNull() ?: tag.value.trimmedText)
                "real" -> list.add(tag.value.trimmedText.toDoubleOrNull() ?: tag.value.trimmedText)
                "map" -> list.add(tag.toLlsdMap())
                "array" -> list.add(tag.toLlsdList())
            }
        }
        return list
    }
}