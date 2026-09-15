package io.github.koollsl.lsl.preprocessor

import com.intellij.openapi.components.Service
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import io.github.koollsl.lsl.utils.getPathKey

@Service(Service.Level.PROJECT)
class LslIncludesCollector(private val project: Project) {

    companion object {
        const val MAX_INCLUDE_DEPTH = 30

        fun getInstance(project: Project): LslIncludesCollector =
            project.getService(LslIncludesCollector::class.java)
    }

    /**
     * Primary entry point for Annotator, Inspections, and Preprocessor Engine.
     * Retains IDE caching bound to file edits and VFS structural changes.
     */
    fun getIncludedFiles(file: PsiFile?): Set<PsiFile> {
        if (file == null || !file.isValid) return emptySet()

        return try {
            CachedValuesManager.getCachedValue(file) {
                val visitedPaths = mutableSetOf<String>()
                // Seed root file path key to catch immediate self-inclusions
                visitedPaths.add(file.getPathKey())

                val result = collectIncludedLslmFiles(file, depth = 0, visitedPaths)

                // Dynamic Dependency List:
                // 1. Root file
                // 2. All transitively included sub-files
                // 3. VFS modifications (for file creates/deletes)
                val dependencies = mutableListOf<Any>(file, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
                dependencies.addAll(result)

                CachedValueProvider.Result.create(result, dependencies)

                // Key Fix: Bind cache invalidation to this specific file and VFS structure changes
                // (file additions/deletions/renames), preventing project-wide keystroke cache misses.
//                CachedValueProvider.Result.create(
//                    result,
//                    file,
//                    VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS
//                )
            } ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /**
     * Recursive collection of included .lslm files starting from a given PSI file.
     */
    private fun collectIncludedLslmFiles(
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

            val path = includedPsi.getPathKey()

            // Check and add to global seenPaths BEFORE recursing down
            if (seenPaths.add(path)) {
                // 1. Traverse child includes first (post-order / bottom-up dependency ordering)
                //LslDebug.log("ADD: name='${includedPsi.name}'")

                val childIncludes = collectIncludedLslmFiles(
                    file = includedPsi,
                    depth = depth + 1,
                    seenPaths = seenPaths
                )

                // 2. Add the resolved include itself
                rawResult.addAll(childIncludes)
                rawResult.add(includedPsi)
            } else {
                //LslDebug.log("DUP: name='${includedPsi.name}', path='$path'")
            }
        }

        return rawResult
    }

    /**
     * Used by Reference targets: Resolves a relative or module-root `#include` path to a PsiFile.
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

        val parentDir = containingVF?.parent

        // --- 3. Resolve module content roots ---
        val allRoots = getModuleAndRoots(containingFile)

        // --- 4. Search strategies (Index-accelerated) ---
        // Uses FilenameIndex instead of recursive directory iteration for speed.
        val virtualFile =
            // A. Relative to containing file (CRITICAL for ../../Lib/...)
            parentDir?.findFileByRelativePath(targetPath)
            // B. Search in module content roots
                ?: allRoots.firstNotNullOfOrNull { it.findFileByRelativePath(targetPath) }
                // C. Fast index-backed lookup across project
                ?: FilenameIndex.getVirtualFilesByName(cleanName, GlobalSearchScope.projectScope(project)).firstOrNull()
                ?: return null

        // --- 5. Prevent recursive includes ---
        if (virtualFile.getPathKey() in visitedFiles) return null

        // --- 6. Sync document --- NO! Laggy and useless
//        val doc = FileDocumentManager.getInstance().getDocument(virtualFile)
//        if (doc != null && PsiDocumentManager.getInstance(project).isUncommited(doc)) {
//            PsiDocumentManager.getInstance(project).commitDocument(doc)
//        }

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
     * Fast line-by-line inspection using CharSequence to avoid heavy String allocations on keystrokes.
     */
    private fun extractIncludePaths(file: PsiFile): List<String> {
        val text = file.viewProvider.contents
        val paths = mutableListOf<String>()

        var start = 0
        val length = text.length

        while (start < length) {
            var end = text.indexOf('\n', start)
            if (end == -1) end = length

            // Fast inline whitespace skip
            var lineStart = start
            while (lineStart < end && text[lineStart].isWhitespace()) {
                lineStart++
            }

            // Check for #include prefix directly in CharSequence
            if (hasPrefixAt(text, lineStart, "#include")) {
                val pathStart = lineStart + 8 // length of "#include"
                val rawPath = text.subSequence(pathStart, end).toString().trim()
                if (rawPath.isNotEmpty()) {
                    paths.add(rawPath)
                }
            }

            start = end + 1
        }
        return paths
    }

    private fun hasPrefixAt(seq: CharSequence, index: Int, prefix: String): Boolean {
        if (index + prefix.length > seq.length) return false
        for (i in prefix.indices) {
            if (seq[index + i] != prefix[i]) return false
        }
        return true
    }

    /**
     * Lightweight scanner to extract direct include path strings from PSI file text.
     */
//    private fun extractIncludePaths(file: PsiFile): List<String> {
//        val text = file.text ?: return emptyList()
//        val paths = mutableListOf<String>()
//
//        // Fast line-by-line inspection for `#include` directives
//        text.lineSequence().forEach { line ->
//            val trimmed = line.trim()
//            if (trimmed.startsWith("#include")) {
//                val path = trimmed.removePrefix("#include").trim()
//                if (path.isNotEmpty()) {
//                    paths.add(path)
//                }
//            }
//        }
//        return paths
//    }

    private fun getModuleAndRoots(file: PsiFile): List<VirtualFile> {
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