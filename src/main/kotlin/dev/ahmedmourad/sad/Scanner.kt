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
            '(' -> collector.capture(LEFT_PAREN)
            ')' -> collector.capture(RIGHT_PAREN)
            '{' -> collector.capture(LEFT_BRACE)
            '}' -> collector.capture(RIGHT_BRACE)
            ',' -> collector.capture(COMMA)
            '-' -> collector.capture(MINUS)
            '+' -> collector.capture(PLUS)
            ';' -> collector.capture(SEMICOLON)
            '*' -> collector.capture(STAR)
            '.' -> collector.capture(if (navigator.advanceIf('.')) DOT_DOT else DOT)
            ':' -> collector.capture(if (navigator.advanceIf(':')) COLON_COLON else COLON)
            '!' -> collector.capture(if (navigator.advanceIf('=')) BANG_EQUAL else BANG)
            '=' -> collector.capture(if (navigator.advanceIf('=')) EQUAL_EQUAL else EQUAL)
            '<' -> collector.capture(if (navigator.advanceIf('=')) LESS_EQUAL else LESS)
            '>' -> collector.capture(if (navigator.advanceIf('=')) GREATER_EQUAL else GREATER)
            '/' -> when {
                navigator.advanceIf('/') -> comment(navigator)
                navigator.advanceIf('*') -> multilineComment(navigator)
                else -> collector.capture(SLASH)
            }
            ' ', '\r', '\t' -> Unit
            '\n' -> navigator.moveToNextLine()
            '\'' -> character(navigator, collector)
            '"' -> if (navigator.advanceIf("\"\"")) multilineString(navigator, collector) else string(navigator, collector)
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

    private fun character(navigator: SourceNavigator, collector: TokenCollector) {

        while (!navigator.isAtEnd() && navigator.peekChar() !in arrayOf('\'', '\n')) {
            navigator.advance()
        }

        if (navigator.isAtEnd() || navigator.peekChar() == '\n') {
            reportError(
                navigator,
                "Incorrect character literal",
                column = navigator.currentTokenStartColumn()
            )
            return
        }

        // The closing '
        navigator.advance()

        when (navigator.currentTokenLength()) {

            2 -> {
                reportError(
                    navigator,
                    "Empty character literal"
                )
                return
            }

            3 -> collector.captureCharacter()

            else -> {
                reportError(
                    navigator,
                    "Too many characters in a character literal \"${navigator.currentTokenAsLexeme()}\"",
                    column = navigator.currentTokenStartColumn()
                )
                return
            }
        }
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

        // The closing "
        navigator.advance()

        collector.captureString()
    }

    private fun multilineString(navigator: SourceNavigator, collector: TokenCollector) {

        while (!navigator.isAtEnd() && navigator.peek(count = 3) != "\"\"\"") {
            if (navigator.peekChar() == '\n') {
                navigator.advance()
                navigator.moveToNextLine()
            } else {
                navigator.advance()
            }
        }

        // Unterminated String
        if (navigator.isAtEnd()) {
            reportError(
                navigator,
                "Unterminated String",
                column = navigator.currentColumn() - 2
            )
            return
        }

        // The closing """
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

        // Look for a fractional part
        if (navigator.peekChar() == '.' && isNormalDigit(navigator.peekChar(offset = 1))) {
            navigator.advance()
            while (isNormalDigit(navigator.peekChar())) {
                navigator.advance()
            }
        }

        collector.captureNumber()
    }

    private fun comment(navigator: SourceNavigator) {
        // A normal comment goes until the end of the line.
        while (!navigator.isAtEnd() && navigator.peekChar() != '\n') {
            navigator.advance()
        }
    }

    private fun multilineComment(navigator: SourceNavigator) {

        while (!navigator.isAtEnd() && navigator.peek(count = 2) != "*/") {
            when {
                navigator.advanceIf("/*") -> {
                    multilineComment(navigator)
                }
                navigator.peekChar() == '\n' -> {
                    navigator.advance()
                    navigator.moveToNextLine()
                }
                else -> {
                    navigator.advance()
                }
            }
        }

        // The closing */
        repeat(2) {
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

        fun advanceIf(expected: String): Boolean {

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

        fun advanceIf(expected: Char): Boolean {
            return advanceIf(expected.toString())
        }

        fun peek(count: Int = 1, offset: Int = 0): String {
            return if (isAtEnd(offset + count + 1)) {
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

        fun currentTokenLength(): Int {
            return nextCharIndex() - (lineStart + currentTokenStartColumn())
        }

        fun currentTokenAsLexeme(): String {
            return source.substring(currentTokenStartColumn(), nextCharIndex())
        }

        fun isAtEnd(required: Int = 1): Boolean {
            return next + required - 1 >= source.length
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

        fun capture(type: TokenType, literal: Any? = null, lexeme: String = extractLexeme()) {
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

        fun captureCharacter() {
            // Trim the surrounding quotes.
            val value = source.substring(
                navigator.currentTokenStartIndex() + 1,
                navigator.nextCharIndex() - 1
            )
            capture(CHAR, value)
        }

        fun captureString() {
            // Trim the surrounding quotes.
            val value = source.substring(
                navigator.currentTokenStartIndex() + 1,
                navigator.nextCharIndex() - 1
            )
            capture(STRING, value)
        }

        fun captureMultilineString() {
            // Trim the surrounding quotes.
            val value = source.substring(
                navigator.currentTokenStartIndex() + 3,
                navigator.nextCharIndex() - 3
            )
            capture(STRING, value)
        }

        fun captureNumber() {
            capture(NUMBER, source.substring(
                navigator.currentTokenStartIndex(),
                navigator.nextCharIndex()
            ).toDouble())
        }

        fun captureIdentifierOrKeyword() {
            val lexeme = source.substring(
                navigator.currentTokenStartIndex(),
                navigator.nextCharIndex()
            )
            capture(KEYWORDS[lexeme] ?: IDENTIFIER)
        }

        fun captureEOF() {
            capture(EOF, null, "")
        }

        fun collect(): List<Token> {
            return tokens
        }

        private fun extractLexeme(): String {
            return source.substring(navigator.currentTokenStartIndex(), navigator.nextCharIndex())
        }
    }
}
