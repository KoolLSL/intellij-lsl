package io.github.koollsl.lsl.utils

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

/**
 * Resolves a unique, normalized path key for a VirtualFile.
 */
fun VirtualFile.getPathKey(): String {
    return this.canonicalPath ?: this.path
}

/**
 * Resolves a unique, normalized path key for a PsiFile (handles in-memory copies).
 */
fun PsiFile.getPathKey(): String {
    val vFile = this.virtualFile ?: this.originalFile.virtualFile
    return vFile?.getPathKey() ?: this.viewProvider.virtualFile.path
}
