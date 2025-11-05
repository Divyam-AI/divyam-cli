package ai.divyam.client

import ai.divyam.api.DefaultApi
import ai.divyam.data.model.BodyGenerateAccessToken
import ai.divyam.data.model.ChatCompletionChunk
import ai.divyam.data.model.ChatCompletionDebugResponse
import ai.divyam.data.model.ChatCompletionResponse
import ai.divyam.data.model.ChatRequest
import ai.divyam.data.model.Choice
import ai.divyam.data.model.Message
import ai.divyam.data.model.ResponseCompletedEvent
import ai.divyam.data.model.ResponseCreatedEvent
import ai.divyam.data.model.ResponseEvent
import ai.divyam.data.model.ResponseMessageContent
import ai.divyam.data.model.ResponseOutputItem
import ai.divyam.data.model.ResponseOutputTextDeltaEvent
import ai.divyam.data.model.ResponseRequest
import ai.divyam.data.model.ResponseRole
import ai.divyam.data.model.ResponsesDebugResponse
import ai.divyam.data.model.ResponsesResponse
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Headers
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class DivyamClient(
    emailId: String? = null,
    password: String? = null,
    endpoint: String = "https://api.divyam.ai",
    apiToken: String? = null,
    authorityOverride: String? = null,
    /**
     * Non-production only
     */
    disableTlsVerification: Boolean = false
) : DefaultApi(
    baseUrl = endpoint,
    authorityOverride = authorityOverride,
    httpClientEngine = httpClientEngine(disableTlsVerification),
    httpClientConfig = configureClientConfig(),
    jsonBlock = configureObjectMapper()
) {
    companion object {
        private fun configureObjectMapper(): ObjectMapper.() -> Unit = {
            registerKotlinModule()
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            @Suppress("DEPRECATION")
            enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
        }

        private fun httpClientEngine(disableTlsVerification: Boolean): HttpClientEngine =
            CIO.create(
                block = configureHttpClientEngine(
                    disableTlsVerification
                )
            )

        private fun configureHttpClientEngine(disableTlsVerification: Boolean):
                CIOEngineConfig.() -> Unit = {
            if (disableTlsVerification) {
                https {
                    trustManager = object : X509TrustManager {
                        override fun checkClientTrusted(
                            p0: Array<out X509Certificate>?,
                            p1: String?
                        ) {
                        }

                        override fun checkServerTrusted(
                            p0: Array<out X509Certificate>?,
                            p1: String?
                        ) {
                        }

                        override fun getAcceptedIssuers(): Array<X509Certificate>? =
                            null
                    }
                }
            }
        }

        private fun configureClientConfig(): (HttpClientConfig<*>) -> Unit =
            { config ->
                config.install(HttpRedirect) {
                    checkHttpMethod =
                        false   // follow for all methods (GET, POST, etc.)
                    allowHttpsDowngrade = false
                }
            }

    }

    private val apiToken: String = apiToken ?: run {
        require(emailId != null && password != null) {
            "Email ID and password must be provided if API token is not set."
        }
        runBlocking {
            val response = generateAccessToken(
                bodyGenerateAccessToken =
                    BodyGenerateAccessToken(emailId, password)
            )
            response.bearerToken
        }
    }

    private val mapper = ObjectMapper()

    init {
        mapper.apply(configureObjectMapper())
        setBearerToken(this.apiToken)
    }

    suspend fun chatCompletion(
        chatRequest: ChatRequest,
        customHeaders: Map<String, List<String>> = emptyMap(),
        mockSelector:
        Boolean = false,
        mockModel: Boolean = false
    ): ChatCompletionResponse {
        val response = completionResponse(
            customHeaders,
            mockSelector,
            mockModel,
            chatRequest
        )
        return responseToChatCompletionResponse(chatRequest, response)
    }

    suspend fun chatCompletionDebugMode(
        chatRequest: ChatRequest,
        customHeaders: Map<String, List<String>> = emptyMap(),
        mockSelector: Boolean = false,
        mockModel: Boolean = false
    ): ChatCompletionDebugResponse {
        val response = completionResponse(
            customHeaders,
            mockSelector,
            mockModel,
            chatRequest
        )
        val chatCompletionResponse: ChatCompletionResponse =
            responseToChatCompletionResponse(chatRequest, response)
        val debugResponse = ChatCompletionDebugResponse(
            chatCompletionResponse,
            response.headers.asMap()
        )
        return debugResponse
    }

    suspend fun chatCompletionPayloadMode(
        payload: Any, customHeaders: Map<String, List<String>> = emptyMap(),
        mockSelector: Boolean = false, mockModel: Boolean = false
    ): HttpResponse {
        return completionResponse(
            customHeaders,
            mockSelector,
            mockModel,
            payload
        )
    }

    private suspend fun completionResponse(
        customHeaders: Map<String, List<String>>,
        mockSelector: Boolean,
        mockModel: Boolean,
        chatRequest: Any
    ): HttpResponse {
        val maxRetries = 3
        var retries = maxRetries
        var response: org.openapitools.client.infrastructure.HttpResponse<Any>? =
            null

        while (retries > 0) {
            @Suppress("UNCHECKED_CAST")
            response = chatCompletionsWithResponse(
                requestBody = mapper.readValue(
                    mapper
                        .writeValueAsString(chatRequest), Map::class.java
                ) as
                        Map<String, Any>,
                mockModel = mockModel, mockSelector = mockSelector,
                extraHeaders = customHeaders
            )

            if (response.status == 200) {
                return response.response
            }

            retries--
            if (retries > 0) {
                delay(1000) // wait before retrying
            }
        }

        error(
            "Error getting response: ${response!!.status} - ${
                response.response
            }"
        )
    }

    private suspend fun responseToChatCompletionResponse(
        chatRequest: ChatRequest,
        response: HttpResponse
    ): ChatCompletionResponse {
        return if (chatRequest.stream != true) {
            response.body()
        } else {
            // For now collect the entire response.
            streamingToCompletionResponse(response)
        }
    }

    suspend fun streamingToCompletionResponse(response: HttpResponse): ChatCompletionResponse {
        val channel = response.bodyAsChannel()

        val contentBuilder = StringBuilder()
        var role = "assistant"
        var firstChunk: ChatCompletionChunk? = null
        var finishReason: String? = null

        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: continue
            if (line.isBlank()) continue
            if (line == "data: [DONE]") break

            if (line.startsWith("data: ")) {
                val payload = line.removePrefix("data: ").trim()
                val chunk = mapper.readValue(
                    payload,
                    ChatCompletionChunk::class.java
                )

                if (firstChunk == null) firstChunk = chunk

                val choice = chunk.choices.firstOrNull()
                choice?.delta?.role?.let { role = it }
                choice?.delta?.content?.let { contentBuilder.append(it) }
                if (choice?.finishReason != null) {
                    finishReason = choice.finishReason
                }
            }
        }

        val chunk0 = firstChunk ?: error("No chunks received")

        return ChatCompletionResponse(
            id = chunk0.id,
            objectType = "chat.completion",
            created = chunk0.created,
            model = chunk0.model ?: "unknown",
            choices = listOf(
                Choice(
                    index = 0,
                    message = Message(
                        role = role,
                        content = contentBuilder.toString()
                    ),
                    finishReason = finishReason
                )
            )
        )
    }

    suspend fun responses(
        chatRequest: ResponseRequest,
        customHeaders: Map<String, List<String>> = emptyMap(),
        mockSelector:
        Boolean = false,
        mockModel: Boolean = false
    ): ResponsesResponse {
        val response = responsesResponse(
            customHeaders,
            mockSelector,
            mockModel,
            chatRequest
        )
        return responseToResponsesResponse(chatRequest, response)
    }

    suspend fun responsesDebugMode(
        chatRequest: ResponseRequest,
        customHeaders: Map<String, List<String>> = emptyMap(),
        mockSelector: Boolean = false,
        mockModel: Boolean = false
    ): ResponsesDebugResponse {
        val response = responsesResponse(
            customHeaders,
            mockSelector,
            mockModel,
            chatRequest
        )
        val responsesResponse: ResponsesResponse =
            responseToResponsesResponse(chatRequest, response)
        val debugResponse = ResponsesDebugResponse(
            responsesResponse,
            response.headers.asMap()
        )
        return debugResponse
    }

    suspend fun responsesPayloadMode(
        payload: Any, customHeaders: Map<String, List<String>> = emptyMap(),
        mockSelector: Boolean = false, mockModel: Boolean = false
    ): HttpResponse {
        return responsesResponse(
            customHeaders,
            mockSelector,
            mockModel,
            payload
        )
    }

    private suspend fun responsesResponse(
        customHeaders: Map<String, List<String>>,
        mockSelector: Boolean,
        mockModel: Boolean,
        chatRequest: Any
    ): HttpResponse {
        val maxRetries = 3
        var retries = maxRetries
        var response: org.openapitools.client.infrastructure.HttpResponse<Any>? =
            null

        while (retries > 0) {
            @Suppress("UNCHECKED_CAST")
            response = responsesWithResponse(
                requestBody = mapper.readValue(
                    mapper
                        .writeValueAsString(chatRequest), Map::class.java
                ) as
                        Map<String, Any>,
                mockModel = mockModel, mockSelector = mockSelector,
                extraHeaders = customHeaders
            )

            if (response.status == 200) {
                return response.response
            }

            retries--
            if (retries > 0) {
                delay(1000) // wait before retrying
            }
        }

        error(
            "Error getting response: ${response!!.status} - ${
                response.response
            }"
        )
    }

    private suspend fun responseToResponsesResponse(
        chatRequest: ResponseRequest,
        response: HttpResponse
    ): ResponsesResponse {
        return if (chatRequest.stream != true) {
            response.body()
        } else {
            // For now collect the entire response.
            streamingToResponsesResponse(response)
        }
    }

    suspend fun streamingToResponsesResponse(response: HttpResponse): ResponsesResponse {
        val channel = response.bodyAsChannel()
        var baseResponse: ResponsesResponse? = null

        // Collect partial output by item_id or output_index
        val outputs =
            mutableMapOf<String, MutableList<String>>() // item_id → accumulated text

        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: continue
            if (line.isBlank()) continue
            if (line == "data: [DONE]") break

            if (line.startsWith("data: ")) {
                val payload = line.removePrefix("data: ").trim()
                val event = mapper.readValue(payload, ResponseEvent::class.java)

                when (event) {
                    is ResponseCreatedEvent -> {
                        baseResponse = event.response
                    }

                    is ResponseOutputTextDeltaEvent -> {
                        // Each delta chunk has content_index, item_id, output_index, delta (text)
                        val itemId = event.itemId
                        val textDelta = event.delta
                        outputs.getOrPut(itemId) { mutableListOf() }
                            .add(textDelta)
                    }

                    is ResponseCompletedEvent -> {
                        // The full response with metadata, usage, etc.
                        baseResponse = event.response
                    }

                    else -> {
                        // Handle other event types if needed, e.g., ResponseContentPartAddedEvent
                    }
                }
            }
        }

        val accumulatedOutputs = outputs.map { (_, parts) ->
            ResponseOutputItem(
                role = ResponseRole.ASSISTANT,
                content = listOf(
                    ResponseMessageContent(
                        type = "text",
                        text = parts.joinToString("")
                    )
                )
            )
        }

        // Return the completed response (baseResponse might have metadata, usage, etc.)
        return baseResponse?.copy(output = accumulatedOutputs)
            ?: error("No chunks received")
    }
}

fun Headers.asMap(): MutableMap<String, List<String>> {
    val headers = mutableMapOf<String, List<String>>()
    forEach { header, value ->
        headers[header] = value
    }
    return headers
}
