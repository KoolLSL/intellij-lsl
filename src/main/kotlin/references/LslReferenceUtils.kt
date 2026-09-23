package io.github.koollsl.lsl.references

import KwdbData
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.*
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
        // Safety check 1: Ensure the initial file itself has a valid containing file
        val targetFile = file.containingFile ?: file
        val virtualFile = targetFile.virtualFile ?: return LocalSearchScope(targetFile)
        val isModule = virtualFile.extension?.equals("lslm", ignoreCase = true) == true

        if (!isModule) {
            return LocalSearchScope(targetFile)
        }

        val project = targetFile.project
        val fileName = virtualFile.name

        val matchingPsiFiles = mutableSetOf<PsiFile>()
        matchingPsiFiles.add(targetFile)

        val fileIndex = ProjectRootManager.getInstance(project).fileIndex

        // Restrict search scope to project content roots and target file types
        val projectContentScope = GlobalSearchScope.getScopeRestrictedByFileTypes(
            ProjectScope.getContentScope(project),
            targetFile.fileType
        )

        // UsageSearchContext.IN_CODE: Strictly code references (#include "file.lslm")
        PsiSearchHelper.getInstance(project).processElementsWithWord(
            { element, _ ->
                val containingFile = element.containingFile
                if (containingFile != null) {
                    val targetVFile = containingFile.virtualFile
                    if (targetVFile != null &&
                        fileIndex.isInContent(targetVFile) &&
                        !fileIndex.isExcluded(targetVFile)
                    ) {
                        matchingPsiFiles.add(containingFile)
                    }
                }
                true
            },
            projectContentScope,
            fileName,
            UsageSearchContext.IN_CODE,
            true
        )

        // Safety check 2: Filter out any elements where containingFile is unexpectedly null
        val validFiles = matchingPsiFiles.filter { it.containingFile != null }.toTypedArray()

        if (validFiles.isEmpty()) {
            return LocalSearchScope.EMPTY
        }

        return LocalSearchScope(validFiles)
    }
}