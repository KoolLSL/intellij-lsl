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

    fun testUndefTracking() {
        val defs = mutableMapOf("FOO" to "1", "BAR" to "2")

        LslDirectiveEvaluator.parseAndAddDefine("#define BAZ 3", defs)
        assertEquals("3", defs["BAZ"])

        defs.remove("FOO")
        assertFalse(preprocessor.evaluateCondition("defined(FOO)", defs))
        assertTrue(preprocessor.evaluateCondition("defined(BAR)", defs))
    }

    fun testNestedAndElifConditions() {
        val defs = mapOf("PROD" to "\"PACK\"", "FEATURE" to "1")
        assertTrue(preprocessor.evaluateCondition("PROD=\"PACK\" && FEATURE", defs))
        assertTrue(preprocessor.evaluateCondition("(PROD=\"DEV\" || PROD=\"PACK\") && (FEATURE || !defined(X))", defs))
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
