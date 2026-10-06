package io.github.koollsl.lsl.shared.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "LslSettings",
    storages = [Storage("lsl_settings.xml")]
)
class LslSettings : PersistentStateComponent<LslSettings> {

    var optimizeConstants: Boolean = true
    var formatOnSave: Boolean = false
    var indentSize: Int = 4
    var useCustomDefinitionsFolder: Boolean = false
    var customDefinitionsFolder: String = ""

    var useGithubDefinitions: Boolean
        get() = !useCustomDefinitionsFolder
        set(value) {
            useCustomDefinitionsFolder = !value
        }

    override fun getState(): LslSettings = this

    override fun loadState(state: LslSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: LslSettings
            get() = service()
    }
}