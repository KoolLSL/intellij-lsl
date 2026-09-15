package io.github.koollsl.lsl.preprocessor

/**
 * Handles tokenization, parsing, and evaluation of C-style preprocessor conditions.
 */
object LslDirectiveEvaluator {


    enum class TokenType {
        LPAREN, RPAREN, OR, AND, EQ, NOT_EQ, NOT, IDENTIFIER, STRING, NUMBER
    }

    data class Token(val type: TokenType, val text: String)

    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val n = input.length
        while (i < n) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> {
                    tokens.add(Token(TokenType.LPAREN, "(")); i++
                }

                c == ')' -> {
                    tokens.add(Token(TokenType.RPAREN, ")")); i++
                }

                c == '|' -> {
                    if (i + 1 < n && input[i + 1] == '|') {
                        tokens.add(Token(TokenType.OR, "||"))
                        i += 2
                    } else {
                        tokens.add(Token(TokenType.OR, "|"))
                        i++
                    }
                }

                c == '&' -> {
                    if (i + 1 < n && input[i + 1] == '&') {
                        tokens.add(Token(TokenType.AND, "&&"))
                        i += 2
                    } else {
                        tokens.add(Token(TokenType.AND, "&"))
                        i++
                    }
                }

                c == '=' -> {
                    if (i + 1 < n && input[i + 1] == '=') {
                        tokens.add(Token(TokenType.EQ, "=="))
                        i += 2
                    } else {
                        tokens.add(Token(TokenType.EQ, "="))
                        i++
                    }
                }

                c == '!' -> {
                    if (i + 1 < n && input[i + 1] == '=') {
                        tokens.add(Token(TokenType.NOT_EQ, "!="))
                        i += 2
                    } else {
                        tokens.add(Token(TokenType.NOT, "!"))
                        i++
                    }
                }

                c == '"' -> {
                    val sb = StringBuilder()
                    i++
                    while (i < n && input[i] != '"') {
                        if (input[i] == '\\' && i + 1 < n) i++
                        sb.append(input[i])
                        i++
                    }
                    if (i < n && input[i] == '"') i++
                    tokens.add(Token(TokenType.STRING, sb.toString()))
                }

                c.isDigit() -> {
                    val sb = StringBuilder()
                    while (i < n && (input[i].isDigit() || input[i] == 'x' || input[i] == 'X' || input[i] in 'a'..'f' || input[i] in 'A'..'F')) {
                        sb.append(input[i])
                        i++
                    }
                    tokens.add(Token(TokenType.NUMBER, sb.toString()))
                }

                c.isJavaIdentifierStart() || c == '_' -> {
                    val sb = StringBuilder()
                    while (i < n && (input[i].isJavaIdentifierPart() || input[i] == '_')) {
                        sb.append(input[i])
                        i++
                    }
                    tokens.add(Token(TokenType.IDENTIFIER, sb.toString()))
                }

                else -> i++
            }
        }
        return tokens
    }

    fun stripTrailingComment(text: String): String {
        val lineCommentIdx = text.indexOf("//")
        var cleaned = if (lineCommentIdx != -1) text.substring(0, lineCommentIdx) else text
        val blockCommentIdx = cleaned.indexOf("/*")
        if (blockCommentIdx != -1) {
            cleaned = cleaned.substring(0, blockCommentIdx)
        }
        return cleaned.trim()
    }

    fun parseAndAddDefine(args: String, definitions: MutableMap<String, String>) {
        var cleanArgs = args.trim()
        if (cleanArgs.isEmpty()) return
        if (cleanArgs.startsWith("#")) {
            cleanArgs = cleanArgs.removePrefix("#").trim()
        }
        if (cleanArgs.startsWith("define", ignoreCase = true)) {
            cleanArgs = cleanArgs.substring(6).trim()
        }
        if (cleanArgs.isEmpty()) return
        val equalIdx = cleanArgs.indexOf('=')
        if (equalIdx != -1) {
            val key = cleanArgs.substring(0, equalIdx).trim().substringBefore('(').trim()
            val value = cleanArgs.substring(equalIdx + 1).trim()
            if (key.isNotEmpty()) {
                definitions[key] = value
            }
        } else {
            val parts = cleanArgs.split(Regex("""\s+"""), limit = 2)
            val key = parts[0].trim().substringBefore('(').trim()
            val value = if (parts.size > 1) parts[1].trim() else "1"
            if (key.isNotEmpty()) {
                definitions[key] = value
            }
        }
    }

    private sealed class ExprValue {
        data class BoolVal(val value: Boolean) : ExprValue()
        data class NumVal(val value: Long) : ExprValue()
        data class StrVal(val value: String) : ExprValue()
        data class IdentVal(val name: String) : ExprValue()
        object Undefined : ExprValue()

        fun toBoolean(): Boolean = when (this) {
            is BoolVal -> value
            is NumVal -> value != 0L
            is StrVal -> value.isNotEmpty() && value != "0" && !value.equals("false", ignoreCase = true)
            is IdentVal, Undefined -> false
        }

        fun toNormalizedString(): String = when (this) {
            is BoolVal -> value.toString()
            is NumVal -> value.toString()
            is StrVal -> value
            is IdentVal -> name
            Undefined -> ""
        }
    }

    class ExpressionParser(
        private val tokens: List<Token>,
        private val definitions: Map<String, String>
    ) {
        private var pos = 0

        private fun peek(): Token? = if (pos < tokens.size) tokens[pos] else null

        private fun previous(): Token = tokens[pos - 1]
        private fun check(type: TokenType): Boolean = peek()?.type == type

        private fun match(vararg types: TokenType): Boolean {
            for (type in types) {
                if (check(type)) {
                    pos++
                    return true
                }
            }
            return false
        }

        private fun consume(type: TokenType): Token {
            if (check(type)) return tokens[pos++]
            throw IllegalArgumentException("Expected $type but got ${peek()?.type}")
        }

        fun parse(): Boolean {
            if (tokens.isEmpty()) return false
            return parseOr().toBoolean()
        }

        private fun parseOr(): ExprValue {
            var left = parseAnd()
            while (match(TokenType.OR)) {
                val right = parseAnd()
                left = ExprValue.BoolVal(left.toBoolean() || right.toBoolean())
            }
            return left
        }

        private fun parseAnd(): ExprValue {
            var left = parseEquality()
            while (match(TokenType.AND)) {
                val right = parseEquality()
                left = ExprValue.BoolVal(left.toBoolean() && right.toBoolean())
            }
            return left
        }

        private fun parseEquality(): ExprValue {
            var left = parseUnary()
            while (match(TokenType.EQ, TokenType.NOT_EQ)) {
                val op = previous().type
                val right = parseUnary()
                val eq = areEqual(left, right)
                left = ExprValue.BoolVal(if (op == TokenType.EQ) eq else !eq)
            }
            return left
        }

        private fun parseUnary(): ExprValue {
            if (match(TokenType.NOT)) {
                val right = parseUnary()
                return ExprValue.BoolVal(!right.toBoolean())
            }
            return parsePrimary()
        }

        private fun parsePrimary(): ExprValue {
            if (match(TokenType.LPAREN)) {
                val expr = parseOr()
                consume(TokenType.RPAREN)
                return expr
            }

            if (match(TokenType.NUMBER)) {
                val text = previous().text
                val num = if (text.startsWith("0x", ignoreCase = true)) {
                    text.substring(2).toLongOrNull(16) ?: 0L
                } else {
                    text.toLongOrNull() ?: 0L
                }
                return ExprValue.NumVal(num)
            }

            if (match(TokenType.STRING)) {
                return ExprValue.StrVal(previous().text)
            }

            if (match(TokenType.IDENTIFIER)) {
                val ident = previous().text
                if (ident == "defined") {
                    val hasParen = match(TokenType.LPAREN)
                    if (check(TokenType.IDENTIFIER)) {
                        val target = consume(TokenType.IDENTIFIER).text
                        if (hasParen) consume(TokenType.RPAREN)
                        return ExprValue.BoolVal(definitions.containsKey(target))
                    }
                    return ExprValue.BoolVal(false)
                }
                if (ident.equals("true", ignoreCase = true)) return ExprValue.BoolVal(true)
                if (ident.equals("false", ignoreCase = true)) return ExprValue.BoolVal(false)

                if (definitions.containsKey(ident)) {
                    val rawVal = definitions[ident]?.trim() ?: ""
                    val unquoted = rawVal.removeSurrounding("\"")
                    val num = unquoted.toLongOrNull()
                    return when {
                        num != null -> ExprValue.NumVal(num)
                        unquoted.equals("true", ignoreCase = true) -> ExprValue.BoolVal(true)
                        unquoted.equals("false", ignoreCase = true) -> ExprValue.BoolVal(false)
                        else -> ExprValue.StrVal(unquoted)
                    }
                } else {
                    return ExprValue.IdentVal(ident)
                }
            }

            return ExprValue.Undefined
        }

        private fun areEqual(a: ExprValue, b: ExprValue): Boolean {
            if (a is ExprValue.NumVal && b is ExprValue.NumVal) {
                return a.value == b.value
            }
            val s1 = a.toNormalizedString()
            val s2 = b.toNormalizedString()
            return s1 == s2
        }
    }

}