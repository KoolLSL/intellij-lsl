package io.github.koollsl.lsl.syntax

import LslIcons
import LslLanguage
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class LslColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon = LslIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = LslSyntaxHighlighter()

    override fun getDemoText(): String =
        """
        // Line comment
        /* Block comment */

        <preprocessor>#include "header.lslm"</preprocessor>

        <preprocessor>#ifdef DEBUG</preprocessor>
        <disabled_code>    // This block is disabled</disabled_code>
        <disabled_code>    <builtin_function>llOwnerSay</builtin_function>("Debug active"<punctuation>);</disabled_code>
        <preprocessor>#endif</preprocessor>

        <type>vector</type> <identifier>gPosition</identifier> <operation_sign>=</operation_sign> <builtin_constant>ZERO_VECTOR</builtin_constant><punctuation>;</punctuation>
        <type>integer</type> <identifier>gCounter</identifier> <operation_sign>=</operation_sign> <number>10</number><punctuation>;</punctuation>
        <type>string</type> <identifier>gText</identifier> <operation_sign>=</operation_sign> "hello"<punctuation>;</punctuation>

        <keyword>default</keyword> <braces>{</braces>
            <event>state_entry</event><braces>()</braces> <braces>{</braces>
                <builtin_function>llOwnerSay</builtin_function><braces>("Script initialized: " + (<type>string</type>)<identifier>gCounter</identifier>)</braces><punctuation>;</punctuation>
            <braces>}</braces>

            <event>touch_start</event><braces>(<type>integer</type> <identifier>total_number</identifier>)</braces> <braces>{</braces>
                <identifier>gPosition</identifier> <operation_sign>=</operation_sign> <builtin_function>llGetPos</builtin_function><braces>()</braces><punctuation>;</punctuation>
                <builtin_function>llSay</builtin_function><braces><number>0</number><punctuation>,</punctuation> "Touched"</braces><punctuation>;</punctuation>
                <keyword>if</keyword> <braces><identifier>total_number</identifier> <operation_sign>></operation_sign> <number>1</number></braces> <braces>{</braces>
                    <keyword>jump</keyword> finish<punctuation>;</punctuation>
                <braces>}</braces>
        @finish<punctuation>;</punctuation>
            <braces>}</braces>
        <braces>}</braces>
        """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = mapOf(
        "keyword" to LslColorKeys.KEYWORD,
        "type" to LslColorKeys.TYPE,
        "builtin_function" to LslColorKeys.BUILTIN_FUNCTION,
        "builtin_constant" to LslColorKeys.BUILTIN_CONSTANT,
        "event" to LslColorKeys.EVENT,
        "identifier" to LslColorKeys.IDENTIFIER,
        "number" to LslColorKeys.NUMBER,
        "string" to LslColorKeys.STRING,
        "line_comment" to LslColorKeys.LINE_COMMENT,
        "block_comment" to LslColorKeys.BLOCK_COMMENT,
        "operation_sign" to LslColorKeys.OPERATION_SIGN,
        "braces" to LslColorKeys.BRACES,
        "punctuation" to LslColorKeys.PUNCTUATION,
        "label" to LslColorKeys.LABEL,
        "preprocessor" to LslColorKeys.PREPROCESSOR,
        "disabled_code" to LslColorKeys.DISABLED_CODE,
    )

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = LslLanguage.INSTANCE.displayName

    companion object {
        private val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Keyword", LslColorKeys.KEYWORD),
            AttributesDescriptor("Type", LslColorKeys.TYPE),
            AttributesDescriptor("Built-in function", LslColorKeys.BUILTIN_FUNCTION),
            AttributesDescriptor("Built-in constant", LslColorKeys.BUILTIN_CONSTANT),
            AttributesDescriptor("Event handler", LslColorKeys.EVENT),
            AttributesDescriptor("Identifier", LslColorKeys.IDENTIFIER),
            AttributesDescriptor("Line comment", LslColorKeys.LINE_COMMENT),
            AttributesDescriptor("Block comment", LslColorKeys.BLOCK_COMMENT),
            AttributesDescriptor("Preprocessor directive", LslColorKeys.PREPROCESSOR),
            AttributesDescriptor("Disabled code", LslColorKeys.DISABLED_CODE),
            AttributesDescriptor("Label", LslColorKeys.LABEL),
            AttributesDescriptor("Number", LslColorKeys.NUMBER),
            AttributesDescriptor("String", LslColorKeys.STRING),
            AttributesDescriptor("Braces, brackets and parentheses", LslColorKeys.BRACES),
            AttributesDescriptor("Operator sign", LslColorKeys.OPERATION_SIGN),
            AttributesDescriptor("Punctuation", LslColorKeys.PUNCTUATION),
        )
    }
}