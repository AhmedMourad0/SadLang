package dev.ahmedmourad.sad

import dev.ahmedmourad.sad.TokenType.*

interface Scanner {
    fun scan(source: String): List<Token>
}

//TODO: """ for multi-line strings, multiline comments
class ScannerImpl(private val messageCollector: MessageCollector) : Scanner {

    override fun scan(source: String): List<Token> {

        val navigator = SourceNavigator(source)
        val collector = TokenCollector(source, navigator)

        while (!navigator.isAtEnd()) {
            // We are at the beginning of the next lexeme.
            navigator.moveToNextToken()
            scanToken(collector, navigator)
        }

        collector.captureEOF()

        return collector.collect()
    }

    private fun scanToken(collector: TokenCollector, navigator: SourceNavigator) {
        when (val char = navigator.advance()) {
            '(' -> collector.captureToken(LEFT_PAREN)
            ')' -> collector.captureToken(RIGHT_PAREN)
            '{' -> collector.captureToken(LEFT_BRACE)
            '}' -> collector.captureToken(RIGHT_BRACE)
            ',' -> collector.captureToken(COMMA)
            '.' -> collector.captureToken(DOT)
            '-' -> collector.captureToken(MINUS)
            '+' -> collector.captureToken(PLUS)
            ';' -> collector.captureToken(SEMICOLON)
            '*' -> collector.captureToken(STAR)
            '!' -> collector.captureToken(if (navigator.matchNextChar('=')) BANG_EQUAL else BANG)
            '=' -> collector.captureToken(if (navigator.matchNextChar('=')) EQUAL_EQUAL else EQUAL)
            '<' -> collector.captureToken(if (navigator.matchNextChar('=')) LESS_EQUAL else LESS)
            '>' -> collector.captureToken(if (navigator.matchNextChar('=')) GREATER_EQUAL else GREATER)
            '/' -> if (navigator.matchNextChar('/')) comment(navigator) else collector.captureToken(SLASH)
            ' ', '\r', '\t' -> Unit
            '\n' -> navigator.moveToNextLine()
            '"' -> if (navigator.matchNext("\"\"")) multilineString(navigator, collector) else string(navigator, collector)
            else -> {
                when {
                    isNormalDigit(char) -> number(navigator, collector)
                    isAlpha(char) -> identifierOrKeyword(navigator, collector)
                    else -> reportError(navigator, "Unexpected character: $char")
                }
            }
        }
    }

    private fun reportError(
        navigator: SourceNavigator,
        message: String,
        line: Int = navigator.currentLine(),
        column: Int = navigator.currentColumn(),
        lineAsLexeme: String = navigator.currentLineAsLexeme()
    ) {
        messageCollector.error(
            line,
            column,
            lineAsLexeme,
            message
        )
    }

    private fun string(navigator: SourceNavigator, collector: TokenCollector) {

        while (!navigator.isAtEnd() && navigator.peekChar() !in arrayOf('"', '\n')) {
            navigator.advance()
        }

        // Unterminated String
        if (navigator.isAtEnd() || navigator.peekChar() == '\n') {
            reportError(
                navigator,
                "Unterminated String",
                column = navigator.currentColumn() - 1
            )
            return
        }

        // The closing ".
        navigator.advance()

        collector.captureString()
    }

    private fun multilineString(navigator: SourceNavigator, collector: TokenCollector) {

        while (!navigator.isAtEnd(3) && navigator.peek(count = 3) != "\"\"\"") {
            if (navigator.peekChar() == '\n') {
                navigator.advance()
                navigator.moveToNextLine()
            } else {
                navigator.advance()
            }
        }

        // Unterminated String
        if (navigator.isAtEnd(3)) {
            reportError(navigator, "Unterminated String")
            return
        }

        // The closing """.
        repeat(3) {
            navigator.advance()
        }

        collector.captureMultilineString()
    }

    private fun identifierOrKeyword(navigator: SourceNavigator, collector: TokenCollector) {

        while (isAlphaNumeric(navigator.peekChar())) {
            navigator.advance()
        }

        collector.captureIdentifierOrKeyword()
    }

    private fun number(navigator: SourceNavigator, collector: TokenCollector) {

        while (isNormalDigit(navigator.peekChar())) {
            navigator.advance()
        }

        // Look for a fractional part.
        if (navigator.peekChar() == '.' && isNormalDigit(navigator.peekChar(offset = 1))) {
            navigator.advance()
            while (isNormalDigit(navigator.peekChar())) {
                navigator.advance()
            }
        }

        collector.captureNumber()
    }

    private fun comment(navigator: SourceNavigator) {
        // A comment goes until the end of the line.
        while (!navigator.isAtEnd() && navigator.peekChar() != '\n') {
            navigator.advance()
        }
    }

    private fun isNormalDigit(c: Char): Boolean {
        return c in '0'..'9'
    }

    private fun isAlpha(c: Char): Boolean {
        return c in 'a'..'z' || c in 'A'..'Z' || c == '_'
    }

    private fun isAlphaNumeric(c: Char): Boolean {
        return isAlpha(c) || isNormalDigit(c)
    }

    class SourceNavigator(private val source: String) {

        private var start = 0
        private var next = 0
        private var line = 1
        private var lineStart = start

        private var lineOfCurrentToken = line
        private var columnOfCurrentToken = start

        fun advance(): Char {
            moveToNextChar()
            return source[next - 1]
        }

        fun matchNext(expected: String): Boolean {

            if (isAtEnd(expected.length)) {
                return false
            }

            if (source.substring(next, next + expected.length) != expected) {
                return false
            }

            repeat(expected.length) {
                moveToNextChar()
            }

            return true
        }

        fun matchNextChar(expected: Char): Boolean {
            return matchNext(expected.toString())
        }

        fun peek(count: Int = 1, offset: Int = 0): String {
            return if (isAtEnd(offset + 1)) {
                '\u0000'.toString()
            } else {
                source.substring(offset + next, offset + next + count)
            }
        }

        fun peekChar(offset: Int = 0): Char {
            return peek(count = 1, offset = offset).toCharArray()[0]
        }

        fun moveToNextLine() {
            ++line
            lineStart = next
        }

        private fun moveToNextChar() {
            ++next
        }

        fun moveToNextToken() {
            start = next
            columnOfCurrentToken = start - lineStart
            lineOfCurrentToken = line
        }

        fun currentTokenStartColumn(): Int {
            return columnOfCurrentToken
        }

        fun currentTokenStartLine(): Int {
            return lineOfCurrentToken
        }

        fun isAtEnd(padding: Int = 1): Boolean {
            return next + padding - 1 >= source.length
        }

        fun currentLine(): Int {
            return line
        }

        private fun currentLineEnd(): Int {
            return source.indexOf('\n', next - 1).takeIf { it > 0 } ?: source.length
        }

        fun currentLineAsLexeme(): String {
            return source.substring(lineStart, currentLineEnd())
        }

        fun currentColumn(): Int {
            return next - lineStart - 1
        }

        fun nextCharIndex(): Int {
            return next
        }

        fun currentTokenStartIndex(): Int {
            return start
        }
    }

    class TokenCollector(private val source: String, private val navigator: SourceNavigator) {

        private val tokens: MutableList<Token> = arrayListOf()

        fun captureToken(type: TokenType, literal: Any? = null, lexeme: String = extractLexeme()) {
            tokens.add(
                Token(
                    type,
                    lexeme,
                    literal,
                    navigator.currentTokenStartLine(),
                    navigator.currentTokenStartColumn()
                )
            )
        }

        fun captureString() {
            // Trim the surrounding quotes.
            val value = source.substring(
                navigator.currentTokenStartIndex() + 1,
                navigator.nextCharIndex() - 1
            )
            captureToken(STRING, value)
        }

        fun captureMultilineString() {
            // Trim the surrounding quotes.
            val value = source.substring(
                navigator.currentTokenStartIndex() + 3,
                navigator.nextCharIndex() - 3
            )
            captureToken(STRING, value)
        }

        fun captureNumber() {
            captureToken(NUMBER, source.substring(
                navigator.currentTokenStartIndex(),
                navigator.nextCharIndex()
            ).toDouble())
        }

        fun captureIdentifierOrKeyword() {
            val lexeme = source.substring(
                navigator.currentTokenStartIndex(),
                navigator.nextCharIndex()
            )
            captureToken(KEYWORDS[lexeme] ?: IDENTIFIER)
        }

        fun captureEOF() {
            captureToken(EOF, null, "")
        }

        fun collect(): List<Token> {
            return tokens
        }

        private fun extractLexeme(): String {
            return source.substring(navigator.currentTokenStartIndex(), navigator.nextCharIndex())
        }
    }
}
