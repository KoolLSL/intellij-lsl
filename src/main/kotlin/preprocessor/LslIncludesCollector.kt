package io.github.koollsl.lsl.preprocessor

import com.intellij.openapi.components.Service
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import io.github.koollsl.lsl.utils.LslFileTypeUtils
import io.github.koollsl.lsl.utils.LslFileTypeUtils.isBuildFile
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
                visitedPaths.add(file.getPathKey())

                val result = collectIncludedFiles(file, depth = 0, visitedPaths)

                val dependencies = mutableListOf<Any>(file, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
                dependencies.addAll(result)

                CachedValueProvider.Result.create(result, dependencies)
            } ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /**
     * Recursive collection of included files starting from a given PSI file.
     */
    private fun collectIncludedFiles(
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

                val childIncludes = collectIncludedFiles(
                    file = includedPsi,
                    depth = depth + 1,
                    seenPaths = seenPaths
                )

                // 2. Add the resolved include itself
                rawResult.addAll(childIncludes)
                rawResult.add(includedPsi)
            }
        }

        return rawResult
    }

    /**
     * Used by Reference targets: Resolves a relative or module-root `#include` path to a PsiFile.
     */
    /**
     * Used by Reference targets: Resolves an #include path strictly to an .lslm module PsiFile.
     * Throws IllegalArgumentException if a non-lslm extension (like .lsl or .lslp) is specified.
     */
    fun resolveIncludeFile(
        includedPath: String,
        containingFile: PsiFile,
        visitedFiles: Set<String> = emptySet()
    ): PsiFile? {
        val project = containingFile.project
        if (project.isDisposed || includedPath.isEmpty()) return null

        val fileIndex = ProjectRootManager.getInstance(project).fileIndex

        // --- 1. Normalize path ---
        val cleanPath = includedPath
            .trim('"', '\'', '<', '>', ' ')
            .replace('\\', '/')
            .removePrefix("/")

        val ext = cleanPath.substringAfterLast('.', "").lowercase()
        val hasExtension = cleanPath.contains('.') && ext != cleanPath

        // --- 2. Strict Extension Validation ---
        val targetPath = when {
            !hasExtension -> "$cleanPath.lslm" // #include "TestModule" -> TestModule.lslm
            ext == "lslm" -> cleanPath          // #include "TestModule.lslm" -> TestModule.lslm
            else -> return null
        }

        // --- 3. Resolve containing file directory and module roots ---
        val containingVF = containingFile.virtualFile
            ?: containingFile.originalFile.virtualFile
            ?: containingFile.viewProvider.virtualFile

        val parentDir = containingVF.parent
        val allRoots = getModuleAndRoots(containingFile)
        val cleanName = targetPath.substringAfterLast('/')

        // --- 4. Search strategies for the target .lslm path ---
        val virtualFile: VirtualFile? =
            // A. Relative to containing file
            parentDir?.findFileByRelativePath(targetPath)
            // B. Search in module content roots
                ?: allRoots.firstNotNullOfOrNull { it.findFileByRelativePath(targetPath) }
                // C. Fast index-backed lookup across project
                ?: FilenameIndex.getVirtualFilesByName(cleanName, GlobalSearchScope.projectScope(project))
                    .firstOrNull { vf -> !LslFileTypeUtils.isBuildFile(vf) && !fileIndex.isExcluded(vf) }

        if (virtualFile == null || LslFileTypeUtils.isBuildFile(virtualFile) || fileIndex.isExcluded(virtualFile)) {
            return null
        }

        // --- 5. Prevent recursive includes ---
        if (virtualFile.getPathKey() in visitedFiles) return null

        // --- 6. Return PSI ---
        return PsiManager.getInstance(project).findFile(virtualFile)
    }
    /**
     * Inverse dependency lookup: Finds all source files that transitively include target file.
     */
fun collectDependentSourceFiles(targetHeader: VirtualFile): Set<VirtualFile> {
    val result = mutableSetOf<VirtualFile>()
    val scope = GlobalSearchScope.projectScope(project)
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex

    // Query strictly entry-point extensions (.lslp and .lsl)
    for (ext in LslFileTypeUtils.LSL_SOURCE_EXTENSIONS) {
        val files = FilenameIndex.getAllFilesByExt(project, ext, scope)
        for (vf in files) {
            // Skip build outputs, excluded files, and self
            if (isBuildFile(vf) || fileIndex.isExcluded(vf) || vf == targetHeader) continue

            val psi = PsiManager.getInstance(project).findFile(vf) ?: continue
            val includes = getIncludedFiles(psi)

            if (includes.any { it.virtualFile == targetHeader }) {
                result.add(vf)
            }
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

            var lineStart = start
            while (lineStart < end && text[lineStart].isWhitespace()) {
                lineStart++
            }

            if (hasPrefixAt(text, lineStart)) {
                val pathStart = lineStart + 8 // 8 = "#include".length
                val rawPath = text.subSequence(pathStart, end).toString().trim()
                if (rawPath.isNotEmpty()) {
                    paths.add(rawPath)
                }
            }

            start = end + 1
        }
        return paths
    }

    private fun hasPrefixAt(seq: CharSequence, index: Int): Boolean {
        val prefix = "#include"
        if (index + prefix.length > seq.length) return false
        for (i in prefix.indices) {
            if (seq[index + i] != prefix[i]) return false
        }
        return true
    }

    private fun getModuleAndRoots(file: PsiFile): List<VirtualFile> {
        val containingVF = file.virtualFile
            ?: file.originalFile.virtualFile
            ?: file.viewProvider.virtualFile

        val parentDir = containingVF.parent
        val module = containingVF.let { ModuleUtilCore.findModuleForFile(it, project) }

        val moduleRoots = if (module != null) {
            val modRootManager = ModuleRootManager.getInstance(module)
            (modRootManager.contentRoots.toList() + modRootManager.getSourceRoots(true).toList()).distinct()
        } else {
            emptyList()
        }

        return (listOfNotNull(parentDir) + moduleRoots).distinct()
    }
}