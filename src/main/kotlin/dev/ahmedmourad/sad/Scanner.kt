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

        fun reportError(message: String) {
            messageCollector.error(
                navigator.currentLine(),
                navigator.currentColumn(),
                navigator.currentLineAsLexeme(),
                message
            )
        }

        while (!navigator.isAtEnd()) {
            // We are at the beginning of the next lexeme.
            navigator.moveToNextToken()
            scanToken(::reportError, collector, navigator)
        }

        collector.captureEOF()

        return collector.collect()
    }

    private fun scanToken(
        reportError: (String) -> Unit,
        collector: TokenCollector,
        navigator: SourceNavigator
    ) {
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
            '!' -> collector.captureToken(if (navigator.matchNext('=')) BANG_EQUAL else BANG)
            '=' -> collector.captureToken(if (navigator.matchNext('=')) EQUAL_EQUAL else EQUAL)
            '<' -> collector.captureToken(if (navigator.matchNext('=')) LESS_EQUAL else LESS)
            '>' -> collector.captureToken(if (navigator.matchNext('=')) GREATER_EQUAL else GREATER)
            '/' -> if (navigator.matchNext('/')) comment(navigator) else collector.captureToken(SLASH)
            ' ', '\r', '\t' -> Unit
            '\n' -> navigator.moveToNextLine()
            '"' -> string(reportError, navigator, collector)
            else -> {
                when {
                    isNormalDigit(char) -> number(navigator, collector)
                    isAlpha(char) -> identifierOrKeyword(navigator, collector)
                    else -> reportError("Unexpected character: $char")
                }
            }
        }
    }

    private fun string(
        reportError: (String) -> Unit,
        navigator: SourceNavigator,
        collector: TokenCollector
    ) {

        while (navigator.peek() != '"' && !navigator.isAtEnd()) {
            if (navigator.peek() == '\n') {

                navigator.advance()
                navigator.moveToNextLine()
            } else {
                navigator.advance()
            }
        }

        // Unterminated String
        if (navigator.isAtEnd()) {
            reportError("Unterminated String")
            return
        }

        // The closing ".
        navigator.advance()

        collector.captureString()
    }

    private fun identifierOrKeyword(navigator: SourceNavigator, collector: TokenCollector) {

        while (isAlphaNumeric(navigator.peek())) {
            navigator.advance()
        }

        collector.captureIdentifierOrKeyword()
    }

    private fun number(navigator: SourceNavigator, collector: TokenCollector) {

        while (isNormalDigit(navigator.peek())) {
            navigator.advance()
        }

        // Look for a fractional part.
        if (navigator.peek() == '.' && isNormalDigit(navigator.peekFurther())) {
            navigator.advance()
            while (isNormalDigit(navigator.peek())) {
                navigator.advance()
            }
        }

        collector.captureNumber()
    }

    private fun comment(navigator: SourceNavigator) {
        // A comment goes until the end of the line.
        while (navigator.peek() != '\n' && !navigator.isAtEnd()) {
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

        fun matchNext(expected: Char): Boolean {

            if (isAtEnd()) {
                return false
            }

            if (source[next] != expected) {
                return false
            }

            moveToNextChar()

            return true
        }

        fun peek(): Char {
            return if (isAtEnd()) '\u0000' else source[next]
        }

        fun peekFurther(): Char {
            return if (next + 1 >= source.length) '\u0000' else source[next + 1]
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

        fun isAtEnd(): Boolean {
            return next >= source.length
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
