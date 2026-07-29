/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.chat

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.base.OutputFormat
import ai.divyam.data.model.ChatCompletionResponse
import ai.divyam.data.model.ChatMessage
import ai.divyam.data.model.ChatRequest
import ai.divyam.data.model.ChatRole
import ai.divyam.data.model.EvalGranularity
import ai.divyam.data.model.EvalSmokeTestRequest
import ai.divyam.data.model.InputMessages
import ai.divyam.data.model.ModelApiType
import ai.divyam.data.model.ResponseContentPart
import ai.divyam.data.model.ResponseInputItem
import ai.divyam.data.model.ResponsesRequest
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
import java.time.Instant
import java.util.Scanner
import java.util.TreeMap
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "chat",
    description = ["Command line chatbot"]
)
class ChatCommand : BaseCommand(preferApiToken = true), Callable<Int> {
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
        names = ["--test-eval"],
        paramLabel = "<eval-id>",
        description = [
            "Run the specified LLM_REQUEST_RESPONSE eval against each completed chat turn"
        ],
    )
    private var testEvalId: Int? = null

    @Option(
        names = ["-s", "--sa-id", "--service-account-id"],
        description = [
            "Service account for --test-eval. If omitted, falls back to DIVYAM_SA_ID, then the current config file."
        ],
    )
    private var testEvalServiceAccountId: String? = null

    @Option(
        names = ["-o", "--org-id"],
        description = [
            "Organization for --test-eval. If omitted, falls back to DIVYAM_ORG_ID, then the current config file."
        ],
    )
    private var testEvalOrgId: Int? = null

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

    private data class ChatTurnResult(
        val assistantText: String,
        val chatRequest: ChatRequest? = null,
        val chatResponse: ChatCompletionResponse? = null,
        val responseHeaders: Map<String, Any> = emptyMap(),
        val debugResponse: Any? = null,
    )

    override fun execute(): Int {
        validateEvalSmokeOptions()

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
                    val (turnResult, measuredLatency) = measureAndDisplayTime(
                        computeLatency
                    ) {
                        @Suppress("RunBlockingInSuspendFunction")
                        runBlocking {
                            generateResponse(conversationHistory)
                        }
                    }
                    loaderJob.cancelAndJoin()
                    print("\r")
                    printDivyamResponse(turnResult.assistantText)

                    turnResult.debugResponse?.let(::printDebugResponse)

                    if (computeLatency) {
                        print(ansi().fgGreen().a("Latency: ").reset())
                        println(measuredLatency)
                    }

                    conversationHistory.add(
                        ChatMessage(
                            role = ChatRole.ASSISTANT,
                            content = turnResult.assistantText
                        )
                    )

                    if (testEvalId != null) {
                        try {
                            smokeTestEval(turnResult)
                        } catch (e: Throwable) {
                            printEvalSmokeFailure(e)
                        }
                    }
                }
            } catch (e: Throwable) {
                runBlocking {
                    loaderJob.cancelAndJoin()
                    print("\r")
                }
                printErrorResponse(e)
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

    private fun printErrorResponse(e: Throwable) {
        print(ansi().fgRed().a("Error: ").reset())
        println(getDisplayMessage(e))
        if (showStackTrace) {
            e.printStackTrace()
        }
    }

    private suspend fun generateResponse(
        conversationHistory: List<ChatMessage>,
    ): ChatTurnResult {
        return if (apiType == ModelApiType.COMPLETIONS) {
            generateCompletionsResponse(conversationHistory)
        } else {
            generateResponsesResponse(conversationHistory)
        }
    }

    private suspend fun generateCompletionsResponse(
        conversationHistory: List<ChatMessage>,
    ): ChatTurnResult {
        val chatRequest = ChatRequest(
            model = model,
            messages = conversationHistory.toList(),
            stream = stream
        )
        if (!debug && testEvalId == null) {
            val response = divyamClient.chatCompletion(
                chatRequest = chatRequest, customHeaders = customHeaders,
                mockSelector = isMockSelector, mockModel = isMockModel
            )
            return ChatTurnResult(completionsResponseToString(response))
        } else {
            val response = divyamClient.chatCompletionDebugMode(
                chatRequest = chatRequest, customHeaders = customHeaders,
                mockSelector = isMockSelector, mockModel = isMockModel
            )

            return ChatTurnResult(
                assistantText = completionsResponseToString(response.chatResponse),
                chatRequest = chatRequest,
                chatResponse = response.chatResponse,
                responseHeaders = response.responseHeaders,
                debugResponse = if (debug) response else null,
            )
        }
    }

    private fun completionsResponseToString(response: ChatCompletionResponse): String =
        response.choices.first().message.content

    private suspend fun generateResponsesResponse(
        conversationHistory: List<ChatMessage>,
    ): ChatTurnResult {
        val chatRequest = ResponsesRequest(
            model = model,
            input = InputMessages(conversationHistory.map { msg ->
                ResponseInputItem(
                    role = msg.role.toResponseRole(),
                    content = listOf(
                        ResponseContentPart(
                            type = "input_text",
                            text = msg.content
                        )
                    )
                )
            }),
            stream = stream
        )

        if (!debug) {
            val response = divyamClient.responses(
                chatRequest = chatRequest, customHeaders = customHeaders,
                mockSelector = isMockSelector, mockModel = isMockModel
            )
            return ChatTurnResult(responsesToString(response))
        } else {
            val response = divyamClient.responsesDebugMode(
                chatRequest = chatRequest, customHeaders = customHeaders,
                mockSelector = isMockSelector, mockModel = isMockModel
            )

            return ChatTurnResult(
                assistantText = responsesToString(response.chatResponse),
                debugResponse = response,
            )
        }
    }

    private fun responsesToString(response: ResponsesResponse): String =
        response.output.first().content.joinToString("") { content ->
            content.text
        }

    private fun validateEvalSmokeOptions() {
        if (testEvalId == null) return

        require(!stream) {
            "--test-eval only supports non-streaming chat completions; remove --stream."
        }
        require(apiType == ModelApiType.COMPLETIONS) {
            "--test-eval only supports --api-type COMPLETIONS; RESPONSES is not scoreable as an LLM_REQUEST_RESPONSE record."
        }

        val eval = runBlocking {
            divyamClient.getEval(
                serviceAccountId = getSaId(testEvalServiceAccountId),
                evalId = requireNotNull(testEvalId),
                orgId = getOrgId(testEvalOrgId),
            )
        }
        require(eval.granularity == EvalGranularity.LLM_REQUEST_RESPONSE) {
            "--test-eval requires an LLM_REQUEST_RESPONSE eval; eval ${eval.id} has granularity ${eval.granularity}."
        }
    }

    private fun printDebugResponse(response: Any) {
        print(ansi().fgGreen().a("Debug: ").reset())
        if (outputFormat == OutputFormat.JSON) {
            printJson(response)
        } else {
            printYaml(response)
        }
    }

    private suspend fun smokeTestEval(turnResult: ChatTurnResult) {
        val chatRequest = requireNotNull(turnResult.chatRequest) {
            "--test-eval requires a chat completions request."
        }
        val chatResponse = requireNotNull(turnResult.chatResponse) {
            "--test-eval requires a chat completions response."
        }
        val trafficBucket = getResponseHeader(
            turnResult.responseHeaders,
            "X-Router-Traffic-Bucket",
        ) ?: throw IllegalStateException(
            "Router response did not include X-Router-Traffic-Bucket; the eval smoke test cannot identify the routed traffic bucket."
        )

        val record = linkedMapOf<String, Any>(
            "id" to chatResponse.id,
            "response_id" to chatResponse.id,
            "timestamp" to Instant.ofEpochSecond(chatResponse.created).toString(),
            "traffic_bucket" to trafficBucket,
            "requested_model" to chatRequest.model,
            "request" to serializeRecordValue(chatRequest),
            "response" to serializeRecordValue(chatResponse),
        )
        getResponseHeader(
            turnResult.responseHeaders,
            "X-Requested-Model-Provider",
        )?.let { record["requested_model_provider"] = it }

        val response = divyamClient.smokeTestEval(
            serviceAccountId = getSaId(testEvalServiceAccountId),
            evalId = requireNotNull(testEvalId),
            orgId = getOrgId(testEvalOrgId),
            evalSmokeTestRequest = EvalSmokeTestRequest(record = record),
        )

        print(ansi().fgGreen().a("Eval smoke test: ").reset())
        if (outputFormat == OutputFormat.JSON) {
            printJson(response)
        } else {
            printYaml(response)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun serializeRecordValue(value: Any): Map<String, Any> =
        getJsonMapper().convertValue(value, Map::class.java) as Map<String, Any>

    private fun getResponseHeader(
        responseHeaders: Map<String, Any>,
        name: String,
    ): String? = responseHeaders.entries.firstOrNull {
        it.key.equals(name, ignoreCase = true)
    }?.value?.let { value ->
        when (value) {
            is Iterable<*> -> value.firstOrNull()?.toString()
            is Array<*> -> value.firstOrNull()?.toString()
            else -> value.toString()
        }
    }?.takeIf(String::isNotBlank)

    private fun printEvalSmokeFailure(error: Throwable) {
        print(ansi().fgRed().a("Eval smoke test failed: ").reset())
        println(getDisplayMessage(error))
    }
}
