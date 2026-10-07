import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.github.koollsl.lsl.shared.definitions.DefinitionsSourceManager
import io.github.koollsl.lsl.psi.*
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import java.io.InputStream

class KwdbData(val project: Project) {
    val data: Map<String, Any>
    val generated: LslFile

    var kwdbSourceInfo: String = ""
        private set

    val functions: Map<String, LslFunction>
    val constants: Map<String, LslGlobalVariable>
    val events: Map<String, LslEvent>

    // Central lookup map for pre-formatted HTML documentation strings (tooltip)
    val elementDocumentation = mutableMapOf<String, String>()

    companion object {
        val KWDB_DATA_KEY = Key.create<KwdbData>("KWDB_DATA")

        fun getInstance(project: Project): KwdbData = project.getUserData(KWDB_DATA_KEY) ?: let {
            val data = KwdbData(project)
            project.putUserData(KWDB_DATA_KEY, data)
            data
        }
    }

    init {
        // Load and merge YAML definitions before generating the PSI model used by lookups.
        val stream = obtainYamlStream()
            ?: throw IllegalStateException("YAML stream could not be obtained.")

        val mergedData = mutableMapOf<String, Any>()

        stream.use { s ->
            val loaderOptions = LoaderOptions().apply {
                codePointLimit = 50 * 1024 * 1024
            }
            val yaml = Yaml(loaderOptions)

            for (doc in yaml.loadAll(s)) {
                if (doc is Map<*, *>) {
                    mergeMaps(mergedData, doc)
                }
            }
        }

        data = mergedData

        val source = generateSource()
        generated = LslElementFactory.createFile(project, source)

        functions = PsiTreeUtil.collectElementsOfType(generated, LslFunction::class.java)
            .mapNotNull { fn -> fn.name?.let { it to fn } }
            .toMap()

        constants = PsiTreeUtil.collectElementsOfType(generated, LslGlobalVariable::class.java)
            .mapNotNull { c -> c.name?.let { it to c } }
            .toMap()

        events = PsiTreeUtil.collectElementsOfType(generated, LslEvent::class.java)
            .mapNotNull { ev -> ev.name?.let { it to ev } }
            .toMap()
    }

    fun getByName(name: String?): LslNamedElement? =
        functions[name] ?: constants[name] ?: events[name]

    fun hasElement(element: PsiElement): Boolean =
        PsiTreeUtil.isAncestor(generated, element, true)

    @Suppress("UNCHECKED_CAST")
    private fun mergeMaps(target: MutableMap<String, Any>, source: Map<*, *>) {
        // Merge nested definition sections so later YAML documents can extend earlier ones.
        source.forEach { (k, v) ->
            val key = k.toString()
            if (v != null) {
                val existing = target[key]
                if (existing is MutableMap<*, *> && v is Map<*, *>) {
                    mergeMaps(existing as MutableMap<String, Any>, v)
                } else if (v is Map<*, *>) {
                    val newMap = mutableMapOf<String, Any>()
                    mergeMaps(newMap, v)
                    target[key] = newMap
                } else {
                    target[key] = v
                }
            }
        }
    }

    private fun obtainYamlStream(): InputStream? {
        kwdbSourceInfo = DefinitionsSourceManager.getLslSourceDescription()
        return DefinitionsSourceManager.openLslDefinitions()
    }

    private fun cleanType(typeStr: String?): String = when (typeStr?.lowercase()?.trim()) {
        "quaternion" -> "rotation"
        "float", "integer", "string", "key", "vector", "rotation", "list" -> typeStr.lowercase().trim()
        else -> "integer"
    }

    private fun generateSource(): String {
        elementDocumentation.clear()
        val sb = StringBuilder()

        fun formatParams(entry: Map<*, *>): String {
            // Definitions may represent arguments as either a list or a name-keyed map.
            val rawArgs = entry["arguments"] ?: entry["params"] ?: entry["parameters"]

            val paramList = mutableListOf<Pair<String, String>>()

            when (rawArgs) {
                is List<*> -> {
                    rawArgs.forEachIndexed { index, item ->
                        if (item is Map<*, *>) {
                            val firstKey = item.keys.firstOrNull()?.toString()
                            val firstVal = item.values.firstOrNull()

                            if (firstVal is Map<*, *>) {
                                val pName = firstKey ?: "arg$index"
                                val pType = firstVal["type"]?.toString() ?: "string"
                                paramList.add(Pair(pName, pType))
                            } else {
                                val pName = item["param"]?.toString()
                                    ?: item["name"]?.toString()
                                    ?: firstKey
                                    ?: "arg$index"
                                val pType = item["type"]?.toString()
                                    ?: item["value-type"]?.toString()
                                    ?: "string"
                                paramList.add(Pair(pName, pType))
                            }
                        } else {
                            paramList.add(Pair("arg$index", "string"))
                        }
                    }
                }

                is Map<*, *> -> {
                    rawArgs.forEach { (key, valMap) ->
                        val pName = key.toString()
                        val pType = if (valMap is Map<*, *>) {
                            valMap["type"]?.toString() ?: "string"
                        } else {
                            valMap?.toString() ?: "string"
                        }
                        paramList.add(Pair(pName, pType))
                    }
                }
            }

            return paramList.joinToString(", ") { (pName, pType) ->
                val safeName = pName.replace("-", "_").replace(Regex("[^a-zA-Z0-9_]"), "")
                val validName = if (safeName.isEmpty()) "arg" else safeName
                "${cleanType(pType)} $validName"
            }
        }

        fun formatParamDocs(entry: Map<*, *>): String {
            // Event docs include per-parameter explanations from the canonical definitions.
            val args = entry["arguments"] as? List<*> ?: return ""
            val paramDocs = mutableListOf<String>()

            args.forEach { arg ->
                val argMap = arg as? Map<*, *> ?: return@forEach
                argMap.forEach { (paramName, paramDetails) ->
                    val details = paramDetails as? Map<*, *> ?: return@forEach

                    val type = details["type"] as? String ?: ""
                    val paramTooltip = details["tooltip"] as? String

                    val tooltipStr = if (!paramTooltip.isNullOrBlank()) {
                        val cleanTooltip = paramTooltip.replace("\r\n", " ")
                            .replace("\n", " ")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        " - $cleanTooltip"
                    } else ""

                    paramDocs.add("• $type <b>$paramName</b>$tooltipStr<br>")
                }
            }

            if (paramDocs.isEmpty()) return ""
            return "\n\n" + paramDocs.joinToString("\n")
        }

        fun formatCommentBlock(
            tooltip: String? = null,
            extraInfo: String? = null,
            paramDocs: String? = null
        ): String {
            val sections = listOfNotNull(
                tooltip?.takeIf { it.isNotBlank() },
                extraInfo?.takeIf { it.isNotBlank() },
                paramDocs?.takeIf { it.isNotBlank() }
            )

            if (sections.isEmpty()) return ""

            // Join sections with <p> or <br><br> to force line breaks in HTML hover popups
            return sections.joinToString("<p>")
        }

        // Build both the generated LSL declarations and the documentation lookup as we go.
        // Constants
        (data["constants"] as? Map<*, *>)?.forEach { (name, rawEntry) ->
            val entry = rawEntry as? Map<*, *> ?: return@forEach
            val type = cleanType(entry["type"] as? String)
            val rawValue = entry["value"]?.toString() ?: "0"

            val tooltip = entry["tooltip"] as? String
            val doc = formatCommentBlock(tooltip, null, "")
            if (doc.isNotBlank()) {
                elementDocumentation[name.toString()] = doc
            }

            val value = when (type) {
                "string", "key" -> "\"${rawValue.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
                else -> rawValue
            }
            sb.appendLine("$type $name = $value;")
        }

        // Functions
        (data["functions"] as? Map<*, *>)?.forEach { (name, rawEntry) ->
            val entry = rawEntry as? Map<*, *> ?: return@forEach

            val tooltip = (entry["tooltip"] as? String).orEmpty()
            val sleepVal = (entry["sleep"] as? Number)?.toDouble() ?: 0.0
            val extraInfo = if (sleepVal > 0.0) "[Forced delay: ${sleepVal}s]" else null
            // paramDocs = formatParamDocs(entry) is less useful for functions than for events
            val paramDocs = ""
            val doc = formatCommentBlock(tooltip, extraInfo, paramDocs)
            if (doc.isNotBlank()) {
                elementDocumentation[name.toString()] = doc
            }

            val rawType = entry["return"] as? String ?: entry["type"] as? String
            val returnPrefix = if (!rawType.isNullOrBlank() && rawType != "void") "${cleanType(rawType)} " else ""
            sb.appendLine("$returnPrefix$name(${formatParams(entry)}) {}")
        }

        // Events inside default block
        sb.appendLine("default {")
        sb.appendLine("    // --- LSL System Events ---\n")
        (data["events"] as? Map<*, *>)?.forEach { (name, rawEntry) ->
            val entry = rawEntry as? Map<*, *> ?: return@forEach

            val tooltip = (entry["tooltip"] as? String).orEmpty()
            val paramDocs = formatParamDocs(entry)
            val doc = formatCommentBlock(tooltip, null, paramDocs)
            if (doc.isNotBlank()) {
                elementDocumentation[name.toString()] = doc
            }

            val paramsStr = formatParams(entry)
            sb.appendLine("$name($paramsStr) {}")
        }
        sb.appendLine("}")

        return sb.toString()
    }
}