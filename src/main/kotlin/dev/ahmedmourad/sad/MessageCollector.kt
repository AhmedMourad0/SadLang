package dev.ahmedmourad.sad

interface MessageCollector {
    val hadError: Boolean
    fun error(line: Int, column: Int, message: String)
    fun report(line: Int, column: Int, where: String, message: String)
    fun clearError()
}

class MessageCollectorImpl : MessageCollector {

    override var hadError = false

    override fun error(line: Int, column: Int, message: String) {
        report(line, column, "", message)
    }

    override fun report(line: Int, column: Int, where: String, message: String) {
        System.err.println(
            "[line $line, column: $column] Error$where: $message"
        )
        hadError = true
    }

    override fun clearError() {
        hadError = false
    }
}
