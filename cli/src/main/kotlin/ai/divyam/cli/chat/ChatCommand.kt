package ai.divyam.cli.chat

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.base.OutputFormat
import ai.divyam.client.ChatMessage
import ai.divyam.client.ChatRequest
import ai.divyam.client.ChatRole
import com.fasterxml.jackson.databind.ObjectMapper
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

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private val objectMapper: ObjectMapper by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (outputFormat == OutputFormat.JSON) {
            getJsonMapper()
        } else {
            getYamlMapper()
        }
    }

    override fun execute(): Int {
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
                    val response =
                        generateResponse(conversationHistory, loaderJob)
                    loaderJob.cancelAndJoin()
                    print("\r")
                    printDivyamResponse(response)

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
        return 0 // Return a successful exit code.
    }

    private fun promptUser() {
        print(ansi().fgCyan().a("You: ").reset())
    }

    private fun printDivyamResponse(response: String) {
        print(ansi().fgMagenta().a("Divyam: ").reset())
        println(response)
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
            println(objectMapper.writeValueAsString(response))

            return response.chatResponse.choices.first().message.content
        }
    }
}