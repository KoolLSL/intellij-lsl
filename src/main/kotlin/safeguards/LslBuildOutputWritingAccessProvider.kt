package io.github.koollsl.lsl.safeguards

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.WritingAccessProvider
import io.github.koollsl.lsl.utils.LslFileTypeUtils.isBuildFile

class LslBuildOutputWritingAccessProvider(val project: Project) : WritingAccessProvider() {
    override fun isPotentiallyWritable(file: VirtualFile): Boolean {
        if (isBuildFile(file)) {
            return false
        }
        return true
    }

    override fun requestWriting(files: Collection<VirtualFile>): Collection<VirtualFile> {
        return files.filter { isBuildFile(it) }
    }
}
