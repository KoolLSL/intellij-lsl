package io.github.koollsl.lsl.syntax

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey

object LslColorKeys {
    val IDENTIFIER =
        TextAttributesKey.createTextAttributesKey("LSL_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
    val NUMBER = TextAttributesKey.createTextAttributesKey("LSL_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val KEYWORD = TextAttributesKey.createTextAttributesKey("LSL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
    val STRING = TextAttributesKey.createTextAttributesKey("LSL_STRING", DefaultLanguageHighlighterColors.STRING)
    val BLOCK_COMMENT =
        TextAttributesKey.createTextAttributesKey("LSL_BLOCK_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT)
    val LINE_COMMENT =
        TextAttributesKey.createTextAttributesKey("LSL_LINE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)

    val OPERATION_SIGN =
        TextAttributesKey.createTextAttributesKey("LSL_OPERATION_SIGN", DefaultLanguageHighlighterColors.OPERATION_SIGN)

    // Grouped Delimiters & Punctuation
    val BRACES = TextAttributesKey.createTextAttributesKey("LSL_BRACES", DefaultLanguageHighlighterColors.BRACES)
    val BRACKETS = BRACES
    val PARENTHESES = BRACES

    val PUNCTUATION =
        TextAttributesKey.createTextAttributesKey("LSL_PUNCTUATION", DefaultLanguageHighlighterColors.SEMICOLON)
    val DOT = PUNCTUATION
    val SEMICOLON = PUNCTUATION
    val COMMA = PUNCTUATION

    val LABEL = TextAttributesKey.createTextAttributesKey("LSL_LABEL", DefaultLanguageHighlighterColors.LABEL)

    val TYPE = TextAttributesKey.createTextAttributesKey("LSL_TYPE", DefaultLanguageHighlighterColors.KEYWORD)
    val TYPENAME = TYPE
    val TYPES = TYPE

    val BUILTIN_FUNCTION = TextAttributesKey.createTextAttributesKey(
        "LSL_BUILTIN_FUNCTION",
        DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL
    )
    val BUILTIN_FUNCTIONS = BUILTIN_FUNCTION

    val BUILTIN_CONSTANT =
        TextAttributesKey.createTextAttributesKey("LSL_CONSTANT", DefaultLanguageHighlighterColors.CONSTANT)
    val BUILTIN_CONSTANTS = BUILTIN_CONSTANT
    val CONSTANT = BUILTIN_CONSTANT

    val EVENT =
        TextAttributesKey.createTextAttributesKey("LSL_EVENT", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
    val EVENTS = EVENT

    val PREPROCESSOR =
        TextAttributesKey.createTextAttributesKey("LSL_PREPROCESSOR", DefaultLanguageHighlighterColors.METADATA)
    val PREPROCESSORS = PREPROCESSOR

    val DISABLED_CODE =
        TextAttributesKey.createTextAttributesKey("LSL_DISABLED_CODE", CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES)
}