package io.github.koollsl.lsl.preprocessor

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import io.github.koollsl.lsl.utils.LslDebug

@Service(Service.Level.PROJECT)
class LslIncludesCollector(private val project: Project) {

    companion object {
        const val MAX_INCLUDE_DEPTH = 30

        fun getInstance(project: Project): LslIncludesCollector =
            project.getService(LslIncludesCollector::class.java)
    }

    fun annotateIncludes(file: PsiFile?, holder: AnnotationHolder) {
        if (file == null || !file.isValid || project.isDisposed) return

        val document = PsiDocumentManager.getInstance(project).getDocument(file)
        val includePaths = extractIncludePaths(file)

        for (includedPath in includePaths) {
            val resolvedPsi = resolveIncludeFile(includedPath, file)

            if (resolvedPsi == null) {
                // Find line range for error annotation
                val text = file.text
                val index = text.indexOf(includedPath)
                val range = if (index >= 0) {
                    TextRange(index, index + includedPath.length)
                } else if (document != null) {
                    val lineIndex = text.substring(0, index.coerceAtLeast(0)).count { it == '\n' }
                    if (lineIndex in 0 until document.lineCount) {
                        TextRange(document.getLineStartOffset(lineIndex), document.getLineEndOffset(lineIndex))
                    } else file.textRange
                } else {
                    file.textRange
                }

                holder.newAnnotation(HighlightSeverity.ERROR, "Cannot resolve include file '$includedPath'")
                    .range(range)
                    .create()
            }
        }
    }

    /**
     * Primary entry point for Annotator, Inspections, and Preprocessor Engine.
     * Retains IDE caching bound to PSI modification count.
     */
    fun getIncludedFiles(file: PsiFile?): Set<PsiFile> {
        if (file == null || !file.isValid) return emptySet()

        return try {
            CachedValuesManager.getCachedValue(file) {
                val visitedPaths = mutableSetOf<String>()
                val result = collectIncludedLslmFiles(file, depth = 0, visitedPaths)
                CachedValueProvider.Result.create(result, file, PsiModificationTracker.MODIFICATION_COUNT)
            } ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /**
     * Recursive collection of included .lslm files starting from a given PSI file.
     */
    fun collectIncludedLslmFiles(
        file: PsiFile?,
        depth: Int = 0,
        seenPaths: MutableSet<String> = mutableSetOf()
    ): Set<PsiFile> {
        if (file == null || !file.isValid || depth > MAX_INCLUDE_DEPTH || project.isDisposed) return emptySet()

        val rawResult = LinkedHashSet<PsiFile>()

        // Scan direct includes from file
        val includePaths = extractIncludePaths(file)

        for (includedPath in includePaths) {
            val includedPsi = resolveIncludeFile(includedPath, file, seenPaths) ?: continue

            val path = includedPsi.virtualFile?.canonicalPath
                ?: includedPsi.virtualFile?.path
                ?: includedPsi.name

            // Check and add to global seenPaths BEFORE recursing down
            if (seenPaths.add(path)) {
                // 1. Traverse child includes first (post-order / bottom-up dependency ordering)
                LslDebug.log("ADD: name='${includedPsi.name}', path='$path'")

                val childIncludes = collectIncludedLslmFiles(
                    file = includedPsi,
                    depth = depth + 1,
                    seenPaths = seenPaths
                )

                // 2. Add the resolved include itself
                rawResult.addAll(childIncludes)
                rawResult.add(includedPsi)
            } else {
                LslDebug.log("DUP: name='${includedPsi.name}', path='$path'")
            }
        }

        return rawResult
    }


    /**
     * Used by Annotator & Reference targets: Resolves a relative or module-root `#include` path to a PsiFile.
     */
    fun resolveIncludeFile(
        includedPath: String,
        containingFile: PsiFile,
        visitedFiles: Set<String> = emptySet()
    ): PsiFile? {

        val project = containingFile.project
        if (project.isDisposed || includedPath.isEmpty()) return null

        // --- 1. Normalize path ---
        val cleanPath = includedPath
            .trim('"', '\'', '<', '>', ' ')
            .replace('\\', '/')
            .removePrefix("/")

        val targetPath = if (cleanPath.endsWith(".lslm", ignoreCase = true)) cleanPath else "$cleanPath.lslm"
        val cleanName = targetPath.substringAfterLast('/')

        // --- 2. Resolve containing file directory ---
        val containingVF = containingFile.virtualFile
            ?: containingFile.originalFile.virtualFile
            ?: containingFile.viewProvider.virtualFile

        val parentDir = containingVF.parent

        // --- 3. Resolve module content roots ---
        val allRoots = getModuleAndRoots(containingFile)
        //DEBUG("    parentDir='${containingFile?.virtualFile?.parent?.path}'")
        //DEBUG("    allRoots=${allRoots.map { it.path }}")

        // --- 4. Search strategies (NO INDEXING) ---
        fun searchInRoot(root: VirtualFile): VirtualFile? {
            var found: VirtualFile? = null

            VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Any>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (!file.isDirectory && file.name.equals(cleanName, ignoreCase = true)) {
                        found = file
                        return false // stop recursion
                    }
                    return true
                }
            })

            return found
        }

        val virtualFile =
            // A. Relative to containing file (CRITICAL for ../../Lib/...)
            parentDir?.findFileByRelativePath(targetPath)
            // B. Search in module content roots
                ?: allRoots.firstNotNullOfOrNull { it.findFileByRelativePath(targetPath) }
                // C. Direct VFS walk in all module roots (NO index)
                ?: allRoots.firstNotNullOfOrNull { searchInRoot(it) }
                ?: return null

        // --- 5. Prevent recursive includes ---
        val canonical = virtualFile.canonicalPath ?: virtualFile.path
        if (canonical in visitedFiles) return null

        // --- 6. Sync document ---
        val doc = FileDocumentManager.getInstance().getDocument(virtualFile)
        if (doc != null && PsiDocumentManager.getInstance(project).isUncommited(doc)) {
            PsiDocumentManager.getInstance(project).commitDocument(doc)
        }

        // --- 7. Try PSI ---
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)

        // --- 8. Fallback PSI creation ---
        if (psiFile == null) return null

        return psiFile
    }

    /**
     * Inverse dependency lookup: Finds all .lslp files that transitively include the specified header.
     * Useful for triggering re-highlighting / re-analysis when an .lslm file changes.
     */
    fun collectDependentLslpFiles(targetLslm: VirtualFile): Set<VirtualFile> {
        val result = mutableSetOf<VirtualFile>()
        val scope = GlobalSearchScope.projectScope(project)
        val lslpFiles = FilenameIndex.getAllFilesByExt(project, "lslp", scope)
        for (lslpVirtualFile in lslpFiles) {
            val lslpPsi = PsiManager.getInstance(project).findFile(lslpVirtualFile) ?: continue
            // Use the collector to inspect included headers
            val includes = getIncludedFiles(lslpPsi)

            if (includes.any { it.virtualFile == targetLslm }) {
                result.add(lslpVirtualFile)
            }
        }

        return result
    }

    /**
     * Lightweight scanner to extract direct include path strings from PSI file text.
     */
    fun extractIncludePaths(file: PsiFile): List<String> {
        val text = file.text ?: return emptyList()
        val paths = mutableListOf<String>()

        // Fast line-by-line inspection for `#include` directives
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#include")) {
                val path = trimmed.removePrefix("#include").trim()
                if (path.isNotEmpty()) {
                    paths.add(path)
                }
            }
        }
        return paths
    }

    fun getModuleAndRoots(file: PsiFile): List<VirtualFile> {
        val containingVF = file.virtualFile
            ?: file.originalFile.virtualFile
            ?: file.viewProvider.virtualFile

        val parentDir = containingVF?.parent
        val module = containingVF?.let { ModuleUtilCore.findModuleForFile(it, project) }

        val moduleRoots = if (module != null) {
            val modRootManager = ModuleRootManager.getInstance(module)
            (modRootManager.contentRoots.toList() + modRootManager.getSourceRoots(true).toList()).distinct()
        } else {
            emptyList()
        }

        return (listOfNotNull(parentDir) + moduleRoots).distinct()
    }

}