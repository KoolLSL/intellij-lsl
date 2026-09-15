package io.github.koollsl.lsl.references

import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.*
import io.github.koollsl.lsl.KwdbData
import io.github.koollsl.lsl.psi.LslNamedElement

object LslReferenceUtils {

    fun findNamedElement(from: PsiElement, name: String): LslNamedElement? {
        // Look for existing identifier in current parent node first.
        val existingIdentifierInCurrentScope = from.parent.children
            .takeWhile { child -> child != from }
            .filterIsInstance<LslNamedElement>()
            .firstOrNull { child -> child.name == name }

        if (existingIdentifierInCurrentScope != null) {
            return existingIdentifierInCurrentScope
        }

        // Check existing identifiers in another scopes.
        var node = from.parent.parent
        while (node != null) {
            val existingIdentifier = node.children
                .filterIsInstance<LslNamedElement>()
                .firstOrNull { child -> child.name == name }

            if (existingIdentifier != null) {
                return existingIdentifier
            }

            node = node.parent
        }

        // Check in KWDB at last.
        return KwdbData.getInstance(from.project).getByName(name)
    }

    /**
     * Resolves the search scope for an LSL file.
     * Returns a LocalSearchScope targeting the local file for standard .lsl files,
     * or an expanded scope containing all project files referencing the module for .lslm files.
     */
    fun getLslIncludeScope(file: PsiFile): LocalSearchScope {
        val virtualFile = file.virtualFile ?: return LocalSearchScope(file)
        val isModule = virtualFile.extension?.equals("lslm", ignoreCase = true) == true

        if (!isModule) {
            return LocalSearchScope(file)
        }

        val project = file.project
        val fileName = virtualFile.name

        val matchingPsiFiles = mutableListOf<PsiFile>()
        matchingPsiFiles.add(file)

        val fileIndex = ProjectRootManager.getInstance(project).fileIndex

        // Restrict search scope to project content roots and target file types
        val projectContentScope = GlobalSearchScope.getScopeRestrictedByFileTypes(
            ProjectScope.getContentScope(project),
            file.fileType
        )

        // UsageSearchContext.IN_COD: Strictly code references (#include "file.lslm")
        PsiSearchHelper.getInstance(project).processElementsWithWord(
            { element, _ ->
                val containingFile = element.containingFile
                val targetVFile = containingFile?.virtualFile

                if (containingFile != null &&
                    targetVFile != null &&
                    fileIndex.isInContent(targetVFile) &&
                    !fileIndex.isExcluded(targetVFile) &&
                    containingFile !in matchingPsiFiles
                ) {
                    matchingPsiFiles.add(containingFile)
                }
                true
            },
            projectContentScope,
            fileName,
            UsageSearchContext.IN_CODE,
            true
        )

        return LocalSearchScope(matchingPsiFiles.toTypedArray())
    }
}