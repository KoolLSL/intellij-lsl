package io.github.koollsl.lsl.psi

import LslFileType
import LslIcons
import LslLanguage
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import javax.swing.Icon

class LslFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, LslLanguage.INSTANCE), ItemPresentation {
    override fun getFileType(): FileType = LslFileType.INSTANCE

    override fun toString() = "LSL file"

    override fun getPresentation(): ItemPresentation? = this

    override fun getPresentableText(): String = name.takeUnless { it.isBlank() } ?: "(anonymous)"

    override fun getIcon(unused: Boolean): Icon = LslIcons.FILE

}