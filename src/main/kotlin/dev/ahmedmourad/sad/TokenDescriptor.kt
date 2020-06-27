package dev.ahmedmourad.sad

sealed class Token {
    abstract val line: Int
    abstract val column: Int
}

//TODO: ?, --, ++, @, ^
enum class SymbolType(val lexeme: String) {
    LEFT_PAREN("("),
    RIGHT_PAREN(")"),
    LEFT_BRACE("{"),
    RIGHT_BRACE("}"),
    COMMA(","),
    MINUS("-"),
    PLUS("+"),
    SEMICOLON(";"),
    SLASH("/"),
    STAR("*"),
    BANG("!"),
    BANG_EQUAL("!="),
    EQUAL("="),
    EQUAL_EQUAL("=="),
    GREATER(">"),
    GREATER_EQUAL(">="),
    LESS("<"),
    LESS_EQUAL("<="),
    DOT("."),
    DOT_DOT(".."),
    COLON(":"),
    COLON_COLON("::")
}

data class Symbol(
    val type: SymbolType,
    override val line: Int,
    override val column: Int
) : Token()

sealed class Literal : Token() {

    abstract val lexeme: kotlin.String

    data class Identifier(
        override val lexeme: kotlin.String,
        override val line: Int,
        override val column: Int
    ) : Literal()

    data class Character(
        val literal: Char,
        override val lexeme: kotlin.String,
        override val line: Int,
        override val column: Int
    ) : Literal()

    data class String(
        val literal: kotlin.String,
        override val lexeme: kotlin.String,
        override val line: Int,
        override val column: Int
    ) : Literal()

    data class Number(
        val literal: Double,
        override val lexeme: kotlin.String,
        override val line: Int,
        override val column: Int
    ) : Literal()
}

//TODO: in
enum class KeywordType(val lexeme: String) {
    AND("and"),
    CLASS("class"),
    ELSE("else"),
    FALSE("false"),
    FUN("fun"),
    FOR("for"),
    IF("if"),
    NULL("null"),
    OR("or"),
    PRINT("print"),
    RETURN("return"),
    SUPER("super"),
    THIS("this"),
    TRUE("true"),
    VAR("var"),
    VAL("val"),
    WHILE("while")
}

data class Keyword(
    val type: KeywordType,
    override val line: Int,
    override val column: Int
) : Token()

data class EOF(override val line: Int, override val column: Int) : Token()
