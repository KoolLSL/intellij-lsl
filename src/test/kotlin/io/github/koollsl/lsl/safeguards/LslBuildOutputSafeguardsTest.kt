package io.github.koollsl.lsl.safeguards

import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.koollsl.lsl.utils.LslFileTypeUtils

class LslBuildOutputSafeguardsTest : BasePlatformTestCase() {

    fun testIsBuildFile() {
        val buildFile = LightVirtualFile("build/output.lsl", "default { state_entry() {} }")
        val nestedBuildFile = LightVirtualFile("src/build/output.lsl", "default { state_entry() {} }")
        val srcFile = LightVirtualFile("src/main.lsl", "default { state_entry() {} }")

        assertTrue(LslFileTypeUtils.isBuildFile(buildFile))
        assertTrue(LslFileTypeUtils.isBuildFile(nestedBuildFile))
        assertFalse(LslFileTypeUtils.isBuildFile(srcFile))
        assertFalse(LslFileTypeUtils.isBuildFile(null))
    }

    fun testWritingAccessProvider() {
        val provider = LslBuildOutputWritingAccessProvider(project)
        val buildFile = LightVirtualFile("build/main.lsl", "default { state_entry() {} }")
        val srcFile = LightVirtualFile("src/main.lsl", "default { state_entry() {} }")

        assertFalse(provider.isPotentiallyWritable(buildFile))
        assertTrue(provider.isPotentiallyWritable(srcFile))

        val readOnlyFiles = provider.requestWriting(listOf(buildFile, srcFile))
        assertEquals(listOf(buildFile), readOnlyFiles)
    }

    fun testNotificationMessage() {
        assertEquals(
            "This is a generated build file. Direct changes will be overwritten during preprocessing.",
            LslBuildOutputNotificationProvider.WARNING_TEXT
        )
    }
}
