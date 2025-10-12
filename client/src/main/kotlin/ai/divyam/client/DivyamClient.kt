package ai.divyam.client

import ai.divyam.api.DefaultApi
import ai.divyam.data.model.BodyGenerateAccessToken
import ai.divyam.data.model.ChatCompletionChunk
import ai.divyam.data.model.ChatCompletionDebugResponse
import ai.divyam.data.model.ChatCompletionResponse
import ai.divyam.data.model.ChatRequest
import ai.divyam.data.model.Choice
import ai.divyam.data.model.Message
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
}

fun Headers.asMap(): MutableMap<String, List<String>> {
    val headers = mutableMapOf<String, List<String>>()
    forEach { header, value ->
        headers[header] = value
    }
    return headers
}
