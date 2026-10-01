import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class LslFileType : LanguageFileType(LslLanguage.INSTANCE) {
    override fun getName(): String = "LSL file"
    override fun getDescription(): String = "Linden Script language preprocessor source file"
    override fun getDefaultExtension(): String = "lslp"
    override fun getIcon(): Icon = LslIcons.FILE_LSLP

    companion object {
        @JvmStatic
        val INSTANCE = LslFileType()
    }
}