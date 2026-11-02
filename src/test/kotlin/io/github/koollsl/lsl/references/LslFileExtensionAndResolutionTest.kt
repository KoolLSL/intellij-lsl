package io.github.koollsl.lsl.references

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import KwdbData
import LslFileType
import io.github.koollsl.lsl.inspections.LslUnmatchedDirectiveInspection
import io.github.koollsl.lsl.preprocessor.LslPreprocessorEngine
import io.github.koollsl.lsl.preprocessor.LslIncludesCollector
import io.github.koollsl.lsl.psi.LslExpressionFunctionCall
import io.github.koollsl.lsl.psi.LslFunction
import io.github.koollsl.lsl.psi.LslGlobalVariable
import io.github.koollsl.lsl.psi.LslLValue
import io.github.koollsl.lsl.psi.LslStatementVariable

class LslFileExtensionAndResolutionTest : BasePlatformTestCase() {
    private val preprocessor get() = LslPreprocessorEngine(project)

    fun testDefaultFileExtensionIsLslp() {
        assertEquals("lslp", LslFileType.INSTANCE.defaultExtension)
    }

    fun testResolveFunctionAcrossLslpAndLslmFiles() {
        myFixture.addFileToProject(
            "MathLib.lslm",
            """
integer addNumbers(integer a, integer b) {
    return a + b;
}
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "LegacyLib.lslm",
            """
integer multiplyNumbers(integer a, integer b) {
    return a * b;
}
            """.trimIndent()
        )

        val mainFile = myFixture.configureByText(
            "Main.lslp",
            """
#include "MathLib.lslm"
#include "LegacyLib.lslm"

default {
    state_entry() {
        integer sum = addNumbers(1, 2);
        integer prod = multiplyNumbers(3, 4);
    }
}
            """.trimIndent()
        )

        val calls = com.intellij.psi.util.PsiTreeUtil.collectElementsOfType(
            mainFile,
            LslExpressionFunctionCall::class.java
        )

        val addCall = calls.first { it.functionName == "addNumbers" }
        val multiplyCall = calls.first { it.functionName == "multiplyNumbers" }

        val resolvedAdd = addCall.reference?.resolve()
        assertNotNull(resolvedAdd)
        assertTrue(resolvedAdd is LslFunction)
        assertEquals("addNumbers", (resolvedAdd as LslFunction).name)

        val resolvedMultiply = multiplyCall.reference?.resolve()
        assertNotNull(resolvedMultiply)
        assertTrue(resolvedMultiply is LslFunction)
        assertEquals("multiplyNumbers", (resolvedMultiply as LslFunction).name)
    }

    fun testResolveGlobalVariablesAcrossLslpAndLslmFiles() {
        myFixture.addFileToProject(
            "Config.lslm",
            """
integer CONFIG_TIMEOUT = 30;
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "LegacyConfig.lslm",
            """
string CONFIG_PREFIX = "TEST_";
            """.trimIndent()
        )

        val mainFile = myFixture.configureByText(
            "Consumer.lsl",
            """
#include "Config.lslm"
#include "LegacyConfig.lslm"

default {
    state_entry() {
        integer t = CONFIG_TIMEOUT;
        string p = CONFIG_PREFIX;
    }
}
            """.trimIndent()
        )

        val lValues = com.intellij.psi.util.PsiTreeUtil.collectElementsOfType(
            mainFile,
            LslLValue::class.java
        )

        val timeoutLValue = lValues.first { it.variableName == "CONFIG_TIMEOUT" }
        val prefixLValue = lValues.first { it.variableName == "CONFIG_PREFIX" }

        val resolvedTimeout = timeoutLValue.reference?.resolve()
        assertNotNull(resolvedTimeout)
        assertTrue(resolvedTimeout is LslGlobalVariable)
        assertEquals("CONFIG_TIMEOUT", (resolvedTimeout as LslGlobalVariable).name)

        val resolvedPrefix = prefixLValue.reference?.resolve()
        assertNotNull(resolvedPrefix)
        assertTrue(resolvedPrefix is LslGlobalVariable)
        assertEquals("CONFIG_PREFIX", (resolvedPrefix as LslGlobalVariable).name)
    }

    fun testRenameGlobalVariableUpdatesUsages() {
        val file = myFixture.configureByText(
            "RenameVariable.lslp",
            """
integer last_broadcast_time = 0;

default {
    state_entry() {
        last_broadcast_time = 1;
    }
}
            """.trimIndent()
        )
        val declaration = com.intellij.psi.util.PsiTreeUtil.findChildOfType(
            file,
            LslGlobalVariable::class.java
        )
        assertNotNull(declaration)

        myFixture.renameElement(declaration!!, "LastRelist")

        assertTrue(file.text.contains("integer LastRelist = 0;"))
        assertTrue(file.text.contains("LastRelist = 1;"))
        assertFalse(file.text.contains("last_broadcast_time"))
    }

    fun testRenameLocalVariableUpdatesUsages() {
        val file = myFixture.configureByText(
            "RenameLocalVariable.lslp",
            """
default {
    state_entry() {
        integer oldName = 0;
        oldName = 1;
    }
}
            """.trimIndent()
        )
        val declaration = com.intellij.psi.util.PsiTreeUtil.findChildOfType(
            file,
            LslStatementVariable::class.java
        )
        assertNotNull(declaration)

        myFixture.renameElement(declaration!!, "newName")

        assertTrue(file.text.contains("integer newName = 0;"))
        assertTrue(file.text.contains("newName = 1;"))
        assertFalse(file.text.contains("oldName"))
    }

    fun testKwdbBuiltinsLoaded() {
        val kwdb = KwdbData.getInstance(project)
        assertNotNull(kwdb.constants["NAK"])
        assertNotNull(kwdb.constants["EOF"])
        assertNotNull(kwdb.functions["llSay"])
        assertNotNull(kwdb.events["state_entry"])
    }

    fun testRecursiveAndConditionalIncludeCollection() {
        myFixture.addFileToProject(
            "HeaderB.lslm",
            """
            integer B_VAL = 20;
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "HeaderA.lslm",
            """
            #include "HeaderB.lslm"
            integer A_VAL = 10;
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "HeaderC.lslm",
            """
            integer C_VAL = 30;
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "HeaderD.lslm",
            """
            integer D_VAL = 40;
            """.trimIndent()
        )

        val mainFile = myFixture.configureByText(
            "Root.lslp",
            """
            #define USE_C
            #include "HeaderA.lslm"
            #ifdef USE_C
            #include "HeaderC.lslm"
            #endif
            #ifndef USE_C
            #include "HeaderD.lslm"
            #endif

            default {
                state_entry() {}
            }
            """.trimIndent()
        )

        val included = LslIncludesCollector.getInstance(project).getIncludedFiles(mainFile)
        val includedNames = included.map { it.name }.toSet()

        assertTrue(includedNames.contains("HeaderA.lslm"))
        assertTrue(includedNames.contains("HeaderB.lslm"))
        assertTrue(includedNames.contains("HeaderC.lslm"))
        assertTrue(includedNames.contains("HeaderD.lslm"))
    }

    fun testCyclicIncludeSafety() {
        myFixture.addFileToProject(
            "CycleA.lslm",
            """
            #include "CycleB.lslm"
            integer CYCLE_A = 1;
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "CycleB.lslm",
            """
            #include "CycleA.lslm"
            integer CYCLE_B = 2;
            """.trimIndent()
        )

        val mainFile = myFixture.configureByText(
            "CycleMain.lslp",
            """
            #include "CycleA.lslm"
            default {
                state_entry() {}
            }
            """.trimIndent()
        )

        val included = LslIncludesCollector.getInstance(project).getIncludedFiles(mainFile)
        val includedNames = included.map { it.name }.toSet()

        assertTrue(includedNames.contains("CycleA.lslm"))
        assertTrue(includedNames.contains("CycleB.lslm"))
        assertEquals(2, included.size)
    }

    fun testMissingIncludeDiagnostic() {
        myFixture.configureByText(
            "TestIncludeWarning.lslp",
            """
            #include "missing_file.lslm"
            #ifdef NEVER_DEFINED
            #include "ignored_missing.lslm"
            #endif

            default {
                state_entry() {}
            }
            """.trimIndent()
        )

        myFixture.enableInspections(LslUnmatchedDirectiveInspection())
        val highlights = myFixture.doHighlighting()
        val includeDiagnostics = highlights.filter {
            it.description?.contains("include file") == true ||
                it.description?.contains("missing_file.lslm") == true
        }

        assertTrue(
            "Highlights: ${includeDiagnostics.map { it.description }}",
            includeDiagnostics.any { it.description == "Cannot find include file 'missing_file.lslm'" }
        )
        assertFalse(includeDiagnostics.any { it.description?.contains("ignored_missing.lslm") == true })
    }

    fun testPreprocessIncludeFromBuffer() {
        myFixture.addFileToProject(
            "BufferHeader.lslm",
            """
            integer BUFFER_VAL = 99;
            bufferFunc() {
                llOwnerSay("hello");
            }
            """.trimIndent()
        )

        val mainFile = myFixture.configureByText(
            "BufferMain.lslp",
            """
            #include "BufferHeader.lslm"
            default {
                state_entry() {
                    bufferFunc();
                }
            }
            """.trimIndent()
        )

        val output = preprocessor.preprocessFile(mainFile)
        assertTrue(output.contains("bufferFunc"))
        assertTrue(output.contains("BEGIN INCLUDE: BufferHeader ("))
    }
}
