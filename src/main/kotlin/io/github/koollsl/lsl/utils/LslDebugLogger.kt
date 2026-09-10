package io.github.koollsl.lsl.utils

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiElement
import java.io.File

// How to use it anywhere:
// LslDebug.log( "Hello bug!")
// then open DEBUG_FILE_PATH in VS Code

object LslDebug {

    val isEnabled = true

    // TODO: Resolves cross-platform to IDE system log path
    private val logFile by lazy {
        File("C:/IntelliJBuild/lsl_debug.txt")
    }

    init {
        // Truncate/empty previous log file on IDE startup or class load
        try {
            if (logFile.exists()) {
                logFile.writeText("")
            }
        } catch (_: Exception) {
            // Ignore file access locks during startup
        }
    }

    fun log(message: String, element: PsiElement? = null) {

        if (!isEnabled) return

        // Extract caller class and source line number using Java 9+ StackWalker
        val callerInfo = StackWalker.getInstance()
            .walk { frames ->
                frames
                    .filter { it.className != LslDebug::class.java.name } // Skip LslDebug internal frames
                    .findFirst()
                    .map { frame ->
                        val className = frame.className.substringAfterLast('.')
                        "$className:${frame.lineNumber}"
                    }
                    .orElse("Unknown")
            }

        try {
            var prefix = "[$callerInfo] "
            if (element != null) {
                val virtualFile = element.containingFile?.virtualFile
                val document = virtualFile?.let {
                    FileDocumentManager.getInstance().getDocument(it)
                }
                val elementLineNumber = document?.getLineNumber(element.textOffset)?.plus(1) ?: -1
                val fileName = virtualFile?.name ?: "unknown"
                prefix += "[$fileName:$elementLineNumber] "
            }

            logFile.appendText("$prefix$message\n")
        } catch (_: Exception) {
            // Ignore write errors
        }
    }

}