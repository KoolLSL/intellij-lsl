package io.github.koollsl.lsl.preprocessor

import org.junit.Assert.*
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LslPreprocessorEngineTest : BasePlatformTestCase() {
    private val preprocessor get() = LslPreprocessorEngine(project)

    fun testDefinitionsTracking() {
        val defs = mutableMapOf<String, String>()

        LslDirectiveEvaluator.parseAndAddDefine("PROD=\"PACK\"", defs)
        assertEquals("\"PACK\"", defs["PROD"])

        LslDirectiveEvaluator.parseAndAddDefine("DEBUG 1", defs)
        assertEquals("1", defs["DEBUG"])

        LslDirectiveEvaluator.parseAndAddDefine("FLAG", defs)
        assertEquals("1", defs["FLAG"])

        LslDirectiveEvaluator.parseAndAddDefine("MODE=RELEASE", defs)
        assertEquals("RELEASE", defs["MODE"])

        LslDirectiveEvaluator.parseAndAddDefine("#define C_STYLE_FLAG", defs)
        assertEquals("1", defs["C_STYLE_FLAG"])

        LslDirectiveEvaluator.parseAndAddDefine("#define C_STYLE_VAL=200", defs)
        assertEquals("200", defs["C_STYLE_VAL"])
    }

    fun testConditionPrefixes() {
        val defs = mapOf("PROD" to "\"PACK\"", "DEV" to "0", "ENABLED" to "1")

        assertTrue(preprocessor.evaluateCondition("#if PROD == \"PACK\"", defs))
        assertFalse(preprocessor.evaluateCondition("#if PROD == \"DEV\"", defs))

        assertTrue(preprocessor.evaluateCondition("#ifdef ENABLED", defs))
        assertFalse(preprocessor.evaluateCondition("#ifdef MISSING", defs))

        assertTrue(preprocessor.evaluateCondition("#ifndef MISSING", defs))
        assertFalse(preprocessor.evaluateCondition("#ifndef ENABLED", defs))

        assertTrue(preprocessor.evaluateCondition("#elif ENABLED", defs))
    }

    fun testStringEquality() {
        val defs = mapOf("PROD" to "\"PACK\"", "DEV" to "0")

        assertTrue(preprocessor.evaluateCondition("PROD=\"PACK\"", defs))
        assertTrue(preprocessor.evaluateCondition("PROD==\"PACK\"", defs))
        assertTrue(preprocessor.evaluateCondition("PROD=PACK", defs))
        assertTrue(preprocessor.evaluateCondition("PROD==PACK", defs))
        assertFalse(preprocessor.evaluateCondition("PROD=\"DEV\"", defs))
        assertTrue(preprocessor.evaluateCondition("PROD!=\"DEV\"", defs))
    }

    fun testLogicalOrConditions() {
        val defs = mapOf("PROD" to "\"PACK\"")

        // A | B
        assertTrue(preprocessor.evaluateCondition("PROD=\"DEV\" | PROD=\"PACK\"", defs))
        assertTrue(preprocessor.evaluateCondition("PROD=\"DEV\" || PROD=\"PACK\"", defs))
        assertFalse(preprocessor.evaluateCondition("PROD=\"DEV\" || PROD=\"STAGING\"", defs))

        val defs2 = mapOf("B" to "1")
        assertTrue(preprocessor.evaluateCondition("A | B", defs2))
        assertTrue(preprocessor.evaluateCondition("A || B", defs2))
        assertFalse(preprocessor.evaluateCondition("A || C", defs2))
    }

    fun testLogicalAndConditions() {
        val defs = mapOf("A" to "1", "B" to "1")
        assertTrue(preprocessor.evaluateCondition("A & B", defs))
        assertTrue(preprocessor.evaluateCondition("A && B", defs))

        val defs2 = mapOf("A" to "1")
        assertFalse(preprocessor.evaluateCondition("A && B", defs2))
    }

    fun testNegationAndDefined() {
        val defs = mapOf("PROD" to "\"PACK\"")
        assertTrue(preprocessor.evaluateCondition("defined(PROD)", defs))
        assertTrue(preprocessor.evaluateCondition("defined PROD", defs))
        assertFalse(preprocessor.evaluateCondition("!defined(PROD)", defs))
        assertTrue(preprocessor.evaluateCondition("!defined(DEV)", defs))
        assertTrue(preprocessor.evaluateCondition("!DEV", defs))
        assertFalse(preprocessor.evaluateCondition("!PROD", defs))
    }

    fun testUndefDirectiveRemovesDefinitionFromFollowingConditions() {
        val file = myFixture.configureByText(
            "TestUndef.lslp",
            """
#define FOO 1
#define BAR 2
#undef FOO

default {
    state_entry() {
#ifdef FOO
        llOwnerSay("FOO is still defined");
#else
        llOwnerSay("FOO was undefined");
#endif
#ifdef BAR
        llOwnerSay("BAR is defined");
#endif
    }
}
            """.trimIndent()
        )

        val result = preprocessor.preprocessFile(file)

        assertFalse(result.contains("FOO is still defined"))
        assertTrue(result.contains("FOO was undefined"))
        assertTrue(result.contains("BAR is defined"))
    }

    fun testDefinitionsPropagateThroughNestedIncludesInOrder() {
        myFixture.addFileToProject(
            "Nested.lslm",
            """
#ifdef MODULE_FLAG
integer NESTED_ENABLED = 1;
#endif
#ifndef KOOL_TEST
integer KOOL_TEST_WAS_UNDEFINED_IN_NESTED = 1;
#endif
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "K_BASE.lslm",
            """
#ifdef KOOL_TEST
DEBUG(string s) {
    llSleep(0.04);
}
#endif
#define MODULE_FLAG
#include Nested.lslm
#undef KOOL_TEST
            """.trimIndent()
        )
        val mainFile = myFixture.configureByText(
            "Main.lslp",
            """
#define KOOL_TEST
#include K_BASE.lslm
#ifdef KOOL_TEST
integer KOOL_TEST_SHOULD_BE_UNDEFINED_AFTER_INCLUDE;
#else
integer AFTER_INCLUDE_UNDEF = 1;
#endif
#ifdef MODULE_FLAG
integer MODULE_DEFINE_ESCAPED_INCLUDE = 1;
#endif
            """.trimIndent()
        )

        val processed = preprocessor.processDirectives(
            LslPreprocessorEngine.SourceContext(mainFile, project),
            markIncludes = false
        )

        assertTrue(processed.expandedText.contains("DEBUG(string s)"))
        assertTrue(processed.expandedText.contains("llSleep(0.04);"))
        assertTrue(processed.expandedText.contains("NESTED_ENABLED"))
        assertFalse(processed.expandedText.contains("KOOL_TEST_WAS_UNDEFINED_IN_NESTED"))
        assertFalse(processed.expandedText.contains("KOOL_TEST_SHOULD_BE_UNDEFINED_AFTER_INCLUDE"))
        assertTrue(processed.expandedText.contains("AFTER_INCLUDE_UNDEF"))
        assertTrue(processed.expandedText.contains("MODULE_DEFINE_ESCAPED_INCLUDE"))
    }

    fun testNestedAndElifConditions() {
        val defs = mapOf("PROD" to "\"PACK\"", "FEATURE" to "1")
        assertTrue(preprocessor.evaluateCondition("PROD=\"PACK\" && FEATURE", defs))
        assertTrue(preprocessor.evaluateCondition("(PROD=\"DEV\" || PROD=\"PACK\") && (FEATURE || !defined(X))", defs))
    }

    fun testCachedProcessingDoesNotReuseMutableIncludeContext() {
        myFixture.addFileToProject(
            "CacheInclude.lslm",
            """
#define FROM_INCLUDE
integer INCLUDED_VALUE;
            """.trimIndent()
        )
        val file = myFixture.configureByText(
            "CachedMain.lslp",
            """
#include CacheInclude.lslm
#ifdef FROM_INCLUDE
integer INCLUDE_DEFINE_PROPAGATED;
#endif
            """.trimIndent()
        )
        val engine = preprocessor

        val first = engine.getCachedProcessDirectives(
            LslPreprocessorEngine.SourceContext(file, project)
        )
        val second = engine.getCachedProcessDirectives(
            LslPreprocessorEngine.SourceContext(file, project)
        )

        assertEquals(first, second)
        assertTrue(second.expandedText.contains("INCLUDED_VALUE"))
        assertTrue(second.expandedText.contains("INCLUDE_DEFINE_PROPAGATED"))
    }

    fun testCachedProcessingDoesNotShareResultsAcrossDefinitions() {
        val file = myFixture.configureByText(
            "ContextMain.lslp",
            """
#ifdef EXTERNAL_FLAG
integer EXTERNAL_FLAG_ENABLED;
#else
integer EXTERNAL_FLAG_DISABLED;
#endif
            """.trimIndent()
        )

        val enabled = preprocessor.getCachedProcessDirectives(
            LslPreprocessorEngine.SourceContext(file, project, definitions = mapOf("EXTERNAL_FLAG" to "1"))
        )
        val disabled = preprocessor.getCachedProcessDirectives(
            LslPreprocessorEngine.SourceContext(file, project)
        )

        assertTrue(enabled.expandedText.contains("EXTERNAL_FLAG_ENABLED"))
        assertFalse(enabled.expandedText.contains("EXTERNAL_FLAG_DISABLED"))
        assertTrue(disabled.expandedText.contains("EXTERNAL_FLAG_DISABLED"))
        assertFalse(disabled.expandedText.contains("EXTERNAL_FLAG_ENABLED"))
    }

    fun testSafeFallbacksOnNullOrMalformed() {
        assertEquals(emptyList<com.intellij.openapi.util.TextRange>(), preprocessor.computeDisabledRanges(null))

        // Malformed conditions should safely evaluate to false without throwing
        val defs = emptyMap<String, String>()
        assertFalse(preprocessor.evaluateCondition("", defs))
        assertFalse(preprocessor.evaluateCondition("   ", defs))
        assertFalse(preprocessor.evaluateCondition("(((", defs))
        assertFalse(preprocessor.evaluateCondition("! && ||", defs))
        assertFalse(preprocessor.evaluateCondition("defined()", defs))
        assertFalse(preprocessor.evaluateCondition("#ifdef", defs))
    }

    fun testPurgeNonLslFiles() {
        val tempDir = java.nio.file.Files.createTempDirectory("build_test").toFile()
        try {
            val lslFile = java.io.File(tempDir, "script.lsl").apply { writeText("default {}") }
            val lslpFile = java.io.File(tempDir, "source.lslp").apply { writeText("#define FOO") }
            val lslmFile = java.io.File(tempDir, "header.lslm").apply { writeText("#define BAR") }

            assertTrue(lslFile.exists())
            assertTrue(lslpFile.exists())
            assertTrue(lslmFile.exists())

            preprocessor.purgeNonBuildFiles(tempDir)

            assertTrue(lslFile.exists())
            assertFalse(lslpFile.exists())
            assertFalse(lslmFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
