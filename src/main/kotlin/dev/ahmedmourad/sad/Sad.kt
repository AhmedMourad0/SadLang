package dev.ahmedmourad.sad

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

object Sad {

    private val messageCollector: MessageCollector = MessageCollectorImpl()
    private val LEXER: Lexer = LexerImpl(messageCollector)

    @JvmStatic
    fun main(args: Array<String>) {
        when {
            args.size > 1 -> {
                println("Usage: jsad [script]")
                exitProcess(64)
            }
            args.size == 1 -> {
                runFile(args[0])
            }
            else -> {
                runPrompt()
            }
        }
    }

    private fun runFile(path: String) {
        val bytes = Files.readAllBytes(Paths.get(path))
        run(String(bytes, Charset.defaultCharset()))
        // Indicate an error in the exit code.
        if (messageCollector.hadError) {
            exitProcess(65)
        }
    }

    private fun runPrompt() {
        val input = InputStreamReader(System.`in`)
        val reader = BufferedReader(input)
        while (true) {
            print("> ")
            run(reader.readLine())
            messageCollector.forgiveError()
        }
    }

    private fun run(source: String) {
        // For now, just print the tokens.
        for (token in LEXER.scan(source)) {
            println(token)
        }
    }
}
