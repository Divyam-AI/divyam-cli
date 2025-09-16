package ai.divyam.cli.chat

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.base.OutputFormat
import ai.divyam.client.data.models.ChatMessage
import ai.divyam.client.data.models.ChatRequest
import ai.divyam.client.data.models.ChatRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.fusesource.jansi.Ansi.ansi
import org.fusesource.jansi.AnsiConsole
import picocli.CommandLine
import picocli.CommandLine.Option
import java.util.Scanner
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

@CommandLine.Command(
    name = "chat",
    description = ["Command line chatbot"]
)
class ChatCommand : BaseCommand(), Callable<Int> {
    companion object {
        const val GREETING =
            "Hello, my name is Divyam. I'm a simple chatbot. How may I assist you today?"
    }

    @Option(
        names = ["--model-name"],
        description = ["Required: the model to use"],
        required = true
    )
    private lateinit var model: String

    @Option(
        names = ["--mock-selector"],
        description = ["Optional: uses mock selector"],
    )
    private var isMockSelector: Boolean = false

    @Option(
        names = ["--mock-model"],
        description = ["Optional: uses mock model"],
    )
    private var isMockModel: Boolean = false

    @Option(
        names = ["--debug"],
        description = ["Optional: Prints HTTP response details"],
    )
    private var debug: Boolean = false

    @Option(
        names = ["--latency"],
        description = ["Optional: Measures and prints response latency"],
    )
    private var computeLatency: Boolean = false

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun execute(): Int {
        System.setProperty("jansi.force", "true")

        // Coloured output support.
        AnsiConsole.systemInstall()

        // Display divyam art
        println(
            object {}.javaClass.getResourceAsStream(
                "/divyam-art.txt"
            )?.bufferedReader()?.use { it.readText() })

        printDivyamResponse(GREETING)

        // Conversation history for this chat session.
        // TODO: Trim very long sessions
        val conversationHistory = mutableListOf<ChatMessage>()

        // Chat loop
        val scanner = Scanner(System.`in`)
        while (true) {
            promptUser()

            val userInput: String? =
                if (scanner.hasNextLine()) {
                    scanner.nextLine()
                } else {
                    null
                }

            if (userInput == null || userInput.equals(
                    "exit",
                    ignoreCase = true
                ) || userInput.equals("bye", ignoreCase = true)
            ) {
                printDivyamResponse("Goodbye!")
                break
            }

            conversationHistory.add(
                ChatMessage(
                    role = ChatRole.USER,
                    content = userInput
                )
            )

            // Start the loader...
            val loaderJob = coroutineScope.launch {
                val frames = listOf("|", "/", "-", "\\")
                var i = 0
                while (isActive) {
                    print(
                        ansi().fgGreen().a(
                            "\rThinking ${
                                frames[i % frames
                                    .size]
                            }"
                        ).reset()
                    ) //
                    i++
                    delay(100)
                }
            }

            try {
                runBlocking {
                    val (response, measuredLatency) = measureAndDisplayTime {
                        runBlocking {
                            generateResponse(conversationHistory, loaderJob)
                        }
                    }
                    loaderJob.cancelAndJoin()
                    print("\r")
                    printDivyamResponse(response)

                    if (computeLatency) {
                        print(ansi().fgGreen().a("Latency: ").reset())
                        println(measuredLatency)
                    }
                    conversationHistory.add(
                        ChatMessage(
                            role = ChatRole.ASSISTANT,
                            content = response
                        )
                    )
                }
            } catch (e: Throwable) {
                runBlocking {
                    loaderJob.cancelAndJoin()
                    print("\r")
                }
                throw e
            }
        }

        scanner.close()
        return 0
    }

    private fun promptUser() {
        print(ansi().fgCyan().a("You: ").reset())
    }

    private fun printDivyamResponse(response: String) {
        print(ansi().fgMagenta().a("Divyam: ").reset())
        println(response)
    }

    fun <T> measureAndDisplayTime(block: () -> T): Pair<T, String> {
        if (!computeLatency) {
            return block.invoke() to ""
        }

        var result: T
        val durationNanos = measureNanoTime {
            result = block.invoke()
        }
        val durationMillis = TimeUnit.NANOSECONDS.toMillis(durationNanos)

        val timeString = when {
            durationMillis >= TimeUnit.MINUTES.toMillis(1) -> {
                val minutes = TimeUnit.NANOSECONDS.toMinutes(durationNanos)
                val remainingSeconds =
                    TimeUnit.NANOSECONDS.toSeconds(durationNanos) % 60
                "Took $minutes min, $remainingSeconds sec"
            }

            durationMillis >= 1000 -> {
                String.format("Took %.2f sec", durationNanos / 1_000_000_000.0)
            }

            durationNanos >= 1_000_000 -> {
                "Took $durationMillis ms"
            }

            durationNanos >= 1000 -> {
                String.format("Took %.2f µs", durationNanos / 1000.0)
            }

            else -> {
                "Took $durationNanos ns"
            }
        }
        return result to timeString
    }

    private suspend fun generateResponse(
        conversationHistory: List<ChatMessage>,
        loaderJob: Job
    ): String {
        val chatRequest = ChatRequest(
            model = model,
            messages = conversationHistory
        )
        if (!debug) {
            val response = divyamClient.chatCompletion(
                chatRequest = chatRequest, mockSelector
                = isMockSelector, mockModel = isMockModel
            )
            return response.choices.first().message.content
        } else {
            val response = divyamClient.chatCompletionDebugMode(
                chatRequest = chatRequest, mockSelector
                = isMockSelector, mockModel = isMockModel
            )

            // FIXME: Kludge to stop loader before print.
            loaderJob.cancelAndJoin()

            print("\r")
            print(ansi().fgGreen().a("Debug: ").reset())
            if (outputFormat != OutputFormat.JSON) {
                printJson(response)
            } else {
                printYaml(response)
            }

            return response.chatResponse.choices.first().message.content
        }
    }
}