package ai.divyam.cli.chat

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.base.OutputFormat
import ai.divyam.data.model.ChatCompletionResponse
import ai.divyam.data.model.ChatMessage
import ai.divyam.data.model.ChatRequest
import ai.divyam.data.model.ChatRole
import ai.divyam.data.model.ModelApiType
import ai.divyam.data.model.ResponseContentPart
import ai.divyam.data.model.ResponseInputItem
import ai.divyam.data.model.ResponseRequest
import ai.divyam.data.model.ResponsesResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.fusesource.jansi.Ansi.ansi
import picocli.CommandLine
import picocli.CommandLine.Option
import java.util.Scanner
import java.util.TreeMap
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "chat",
    description = ["Command line chatbot"]
)
class ChatCommand : BaseCommand(), Callable<Int> {
    companion object {
        const val GREETING =
            "Hello, my name is Divyam. I'm a simple chatbot. How may I assist you today?"

        fun parseRawHeaders(rawHeaders: List<String>): Map<String,
                List<String>> {
            val headerMap =
                TreeMap<String, MutableList<String>>(String.CASE_INSENSITIVE_ORDER)

            rawHeaders.forEach { rawHeader ->
                val colonIndex = rawHeader.indexOf(':')
                if (colonIndex > 0) {
                    val name = rawHeader.take(colonIndex).trim()
                    val value = rawHeader.substring(colonIndex + 1).trim()
                    headerMap.computeIfAbsent(name) { mutableListOf() }
                        .add(value)
                } else {
                    System.err.println("Invalid header format: '$rawHeader'")
                }
            }

            return headerMap
        }
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

    @Option(
        names = ["--stream"],
        description = ["Optional: Indicates if the response should be " +
                "streaming"],
    )
    private var stream: Boolean = false

    @Option(
        names = ["--api-type"],
        description = ["Optional: The API type to use for this mode." +
                $$"Valid values are ${COMPLETION-CANDIDATES}"]
    )
    private var apiType: ModelApiType = ModelApiType.COMPLETIONS

    @Option(
        names = ["-H", "--header"],
        description = [
            "Pass custom header(s) to server",
            "Format: 'Name: Value'",
            "Can be used multiple times",
            "Examples:",
            "  -H 'Accept: application/json'",
            "  -H 'Authorization: Bearer token123'",
            "  -H 'Accept: application/json' -H 'Accept: text/plain'"
        ]
    )
    var rawHeaders: List<String> = mutableListOf()

    val customHeaders: Map<String, List<String>> by lazy {
        parseRawHeaders(rawHeaders)
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun execute(): Int {
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
                    )
                    i++
                    delay(100)
                }
            }

            try {
                runBlocking {
                    val (response, measuredLatency) = measureAndDisplayTime(
                        computeLatency
                    ) {
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

    private suspend fun generateResponse(
        conversationHistory: List<ChatMessage>,
        loaderJob: Job
    ): String {
        return if (apiType == ModelApiType.COMPLETIONS) {
            generateCompletionsResponse(conversationHistory, loaderJob)
        } else {
            generateResponsesResponse(conversationHistory, loaderJob)
        }
    }

    private suspend fun generateCompletionsResponse(
        conversationHistory: List<ChatMessage>,
        loaderJob: Job
    ): String {
        val chatRequest = ChatRequest(
            model = model,
            messages = conversationHistory,
            stream = stream
        )
        if (!debug) {
            val response = divyamClient.chatCompletion(
                chatRequest = chatRequest, customHeaders = customHeaders,
                mockSelector = isMockSelector, mockModel = isMockModel
            )
            return completionsResponseToString(response)
        } else {
            val response = divyamClient.chatCompletionDebugMode(
                chatRequest = chatRequest, customHeaders = customHeaders,
                mockSelector = isMockSelector, mockModel = isMockModel
            )

            // FIXME: Kludge to stop loader before print.
            loaderJob.cancelAndJoin()

            print("\r")
            print(ansi().fgGreen().a("Debug: ").reset())
            if (outputFormat == OutputFormat.JSON) {
                printJson(response)
            } else {
                printYaml(response)
            }

            return completionsResponseToString(response.chatResponse)
        }
    }

    private fun completionsResponseToString(response: ChatCompletionResponse): String =
        response.choices.first().message.content

    private suspend fun generateResponsesResponse(
        conversationHistory: List<ChatMessage>,
        loaderJob: Job
    ): String {
        val chatRequest = ResponseRequest(
            model = model,
            input = conversationHistory.map { msg ->
                ResponseInputItem(
                    role = msg.role.toResponseRole(),
                    content = listOf(
                        ResponseContentPart(
                            type = "input_text",
                            text = msg.content
                        )
                    )
                )
            },
            stream = stream
        )

        if (!debug) {
            val response = divyamClient.responses(
                chatRequest = chatRequest, customHeaders = customHeaders,
                mockSelector = isMockSelector, mockModel = isMockModel
            )
            return responsesToString(response)
        } else {
            val response = divyamClient.responsesDebugMode(
                chatRequest = chatRequest, customHeaders = customHeaders,
                mockSelector = isMockSelector, mockModel = isMockModel
            )

            // FIXME: Kludge to stop loader before print.
            loaderJob.cancelAndJoin()

            print("\r")
            print(ansi().fgGreen().a("Debug: ").reset())
            if (outputFormat == OutputFormat.JSON) {
                printJson(response)
            } else {
                printYaml(response)
            }

            return responsesToString(response.chatResponse)
        }
    }

    private fun responsesToString(response: ResponsesResponse): String =
        response.output.first().content.joinToString("") { content ->
            content.text
        }
}