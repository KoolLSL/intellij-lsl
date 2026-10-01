package io.github.koollsl.lsl.utils

import com.intellij.openapi.vfs.VirtualFile

object LslFileTypeUtils {

    // --- Extension Sets ---
    val LSL_EXTENSIONS = setOf("lslp", "lslm", "lsl")
    val LSL_SOURCE_EXTENSIONS = setOf("lslp", "lsl")

    val SLUA_EXTENSIONS = setOf("luau")

    // Extensions permitted to exist in the /build/ directory
    val BUILD_TARGET_EXTENSIONS = setOf("lsl", "luau")

//    val ALL_SOURCE_EXTENSIONS = LSL_SOURCE_EXTENSIONS + SLUA_EXTENSIONS

    // --- Extension Queries ---
    fun getExtension(file: VirtualFile): String =
        file.extension?.lowercase() ?: ""

    // --- Language Classification ---
    fun isLsl(file: VirtualFile): Boolean =
        getExtension(file) in LSL_EXTENSIONS

    fun isSlua(file: VirtualFile): Boolean =
        getExtension(file) in SLUA_EXTENSIONS

    /**
     * Identifies if a file is an include-only LSL module/header (.lslm).
     * These files are resolved recursively during pre-processing, but NEVER produce
     * standalone artifacts in /build/.
     */
    fun isHeaderModule(file: VirtualFile): Boolean =
        getExtension(file) == "lslm"

    // --- Build Output vs Source Classification ---

    /**
     * Identifies if a file is an output artifact residing in a build folder.
     */
    fun isBuildFile(file: VirtualFile?): Boolean {
        if (file == null) return false
        val normalizedPath = file.path.replace('\\', '/')
        val dirPath = normalizedPath.substringBeforeLast('/', "")
        val segments = dirPath.split('/')
        return segments.any { segment ->
            segment.equals("build", ignoreCase = true)
        }
    }

    /**
     * Identifies if a file is source code (LSL or Luau) and NOT an output artifact in /build/.
     */
//    fun isSourceFile(file: VirtualFile): Boolean =
//        getExtension(file) in ALL_SOURCE_EXTENSIONS && !isBuildFile(file)

    fun isLslSource(file: VirtualFile): Boolean =
        isLsl(file) && !isBuildFile(file)

    fun isSluaSource(file: VirtualFile): Boolean =
        isSlua(file) && !isBuildFile(file)
}