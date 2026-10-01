import com.intellij.ide.IconProvider
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import io.github.koollsl.lsl.psi.LslEvent
import io.github.koollsl.lsl.psi.LslState
import io.github.koollsl.lsl.utils.LslFileTypeUtils.isBuildFile
import javax.swing.Icon

object LslIcons {
    // PSI Structure Icons (used by LslEvent, LslFunction, etc.)
    @JvmField val EVENT: Icon = IconLoader.getIcon("/icons/event.svg", LslIcons::class.java)
    @JvmField val STATE: Icon = IconLoader.getIcon("/icons/state.svg", LslIcons::class.java) // Or /icons/state.svg if present

    // File Extension Icons
    @JvmField val FILE_LSL: Icon = IconLoader.getIcon("/icons/lsl.svg", LslIcons::class.java)
    @JvmField val FILE_LSLP: Icon = IconLoader.getIcon("/icons/lslp.svg", LslIcons::class.java)
    @JvmField val FILE_LSLM: Icon = IconLoader.getIcon("/icons/lslm.svg", LslIcons::class.java)

    // Custom or built-in icon references
    val VARIABLE: Icon = IconLoader.getIcon("/icons/variableblue.svg", LslIcons::class.java)
    //val FUNCTION: Icon = AllIcons.Nodes.Function              // Distinct blue "m" method icon
}

class LslIconProvider : IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element is PsiFile) {
            val vf = element.virtualFile ?: element.originalFile.virtualFile ?: return null

            return when (vf.extension?.lowercase()) {
                "lsl" -> {
                    // If the .lsl file lives in /build/, show generated .lsl icon
                    // Otherwise (source .lsl), show the .lslp source icon
                    if (isBuildFile(vf)) {
                        LslIcons.FILE_LSL
                    } else {
                        LslIcons.FILE_LSLP
                    }
                }
                "lslp" -> LslIcons.FILE_LSLP
                "lslm" -> LslIcons.FILE_LSLM
                else -> null
            }
        }

        return when (element) {
            is LslState -> LslIcons.STATE
            is LslEvent -> LslIcons.EVENT
            else -> null
        }
    }
}