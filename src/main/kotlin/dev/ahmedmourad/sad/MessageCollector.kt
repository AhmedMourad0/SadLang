package dev.ahmedmourad.sad

interface MessageCollector {
    val hadError: Boolean
    fun error(line: Int, column: Int, lineAsLexeme: String, message: String)
    fun report(line: Int, column: Int, message: (Int) -> String)
    fun forgiveError()
}

class MessageCollectorImpl : MessageCollector {

    override var hadError = false

    override fun error(line: Int, column: Int, lineAsLexeme: String, message: String) {
        report(line, column) { headLength ->
            "$lineAsLexeme\n${" ".repeat(headLength)}${pointerAt(column)} $message\n"
        }
    }

    private fun pointerAt(column: Int): String {
        return "${" ".repeat(column)}^___"
    }

    override fun report(line: Int, column: Int, message: (Int) -> String) {
        val head = "[line $line, column: $column] Error at: "
        System.err.println(head + message(head.length))
        hadError = true
    }

    override fun forgiveError() {
        hadError = false
    }
}
