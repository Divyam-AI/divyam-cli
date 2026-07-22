/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
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
import ai.divyam.data.model.IssuedServiceAccountApiKey
import ai.divyam.data.model.ResponseMessageContent
import ai.divyam.data.model.ResponseOutputItem
import ai.divyam.data.model.ResponseOutputTextDeltaEvent
import ai.divyam.data.model.ResponseRole
import ai.divyam.data.model.ResponsesDebugResponse
import ai.divyam.data.model.ResponsesRequest
import ai.divyam.data.model.ResponsesResponse
import ai.divyam.data.model.ServiceAccountApiKeyDeleteResponse
import ai.divyam.data.model.ServiceAccountApiKeyRecord
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.client.utils.EmptyContent
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.openapitools.client.infrastructure.RequestConfig
import org.openapitools.client.infrastructure.RequestMethod
import org.openapitools.client.infrastructure.wrap
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.ANY,      // Finds @get:JsonProperty
    isGetterVisibility = JsonAutoDetect.Visibility.ANY,    // Finds boolean getters
    fieldVisibility = JsonAutoDetect.Visibility.NONE,      // FIX: Prevents Java 21 HashMap crash
    creatorVisibility = JsonAutoDetect.Visibility.ANY      // Allows finding the constructor
)
@JsonIgnoreProperties(
    "empty",
    "entries",
    "keys",
    "values",
    "threshold",
    "loadFactor",
    "modCount",
    "size",
    ignoreUnknown = true
)
abstract class OpenAPIHashMapMixin

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
        fun configureObjectMapper(): ObjectMapper.() -> Unit = {
            registerKotlinModule()
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            @Suppress("DEPRECATION")
            enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)

            val index =
                object {}.javaClass.getResourceAsStream("/model-index.txt")
                    ?.bufferedReader()
                    ?.readLines() ?: emptyList()

            index.forEach { className ->
                try {
                    val clazz = Class.forName(className)
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                    if (java.util.Map::class.java.isAssignableFrom(clazz)) {
                        addMixIn(clazz, OpenAPIHashMapMixin::class.java)
                    }
                } catch (_: Exception) {
                    // Class might not be on the classpath in this specific build
                }
            }
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
                body = mapper.readValue(
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
        chatRequest: ResponsesRequest,
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
        chatRequest: ResponsesRequest,
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
                body = mapper.readValue(
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
        chatRequest: ResponsesRequest,
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

    suspend fun listServiceAccountApiKeys(
        serviceAccountId: String,
        extraHeaders: Map<String, List<String>>? = null
    ): List<ServiceAccountApiKeyRecord> {
        val response = serviceAccountApiKeysRequest<List<ServiceAccountApiKeyRecord>>(
            method = RequestMethod.GET,
            serviceAccountId = serviceAccountId,
            extraHeaders = extraHeaders
        )
        return response.body()
    }

    suspend fun createServiceAccountApiKey(
        serviceAccountId: String,
        extraHeaders: Map<String, List<String>>? = null
    ): IssuedServiceAccountApiKey {
        val response = serviceAccountApiKeysRequest<IssuedServiceAccountApiKey>(
            method = RequestMethod.POST,
            serviceAccountId = serviceAccountId,
            extraHeaders = extraHeaders
        )
        return response.body()
    }

    suspend fun deleteServiceAccountApiKey(
        serviceAccountId: String,
        keyId: String,
        extraHeaders: Map<String, List<String>>? = null
    ): ServiceAccountApiKeyDeleteResponse {
        val localVariableAuthNames = listOf("bearer")
        val localVariableHeaders = mutableMapOf<String, List<String>>()
        mergeExtraHeaders(localVariableHeaders, extraHeaders)
        val localVariableConfig = RequestConfig<Unit>(
            RequestMethod.DELETE,
            "/v1/service_accounts/{service_account_id}/api_keys/{key_id}/"
                .replace("{service_account_id}", serviceAccountId)
                .replace("{key_id}", keyId),
            query = mutableMapOf(),
            headers = localVariableHeaders,
            requiresAuthentication = false,
        )
        val response = request(
            localVariableConfig,
            EmptyContent,
            localVariableAuthNames
        )

        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            error("Error: ${response.status} - ${response.bodyAsText()}")
        }
        return mapper.readValue(
            response.bodyAsText(),
            ServiceAccountApiKeyDeleteResponse::class.java
        )
    }

    private suspend inline fun <reified T : Any> serviceAccountApiKeysRequest(
        method: RequestMethod,
        serviceAccountId: String,
        extraHeaders: Map<String, List<String>>? = null
    ): org.openapitools.client.infrastructure.HttpResponse<T> {
        val localVariableAuthNames = listOf("bearer")
        val localVariableHeaders = mutableMapOf<String, List<String>>()
        mergeExtraHeaders(localVariableHeaders, extraHeaders)
        val localVariableConfig = RequestConfig<Any?>(
            method,
            "/v1/service_accounts/{service_account_id}/api_keys/"
                .replace("{service_account_id}", serviceAccountId),
            query = mutableMapOf(),
            headers = localVariableHeaders,
            requiresAuthentication = false,
        )
        val response = request(
            localVariableConfig,
            EmptyContent,
            localVariableAuthNames
        ).wrap<T>()

        if (response.status != 200 && response.status != 201) {
            error("Error: ${response.status} - ${response.response.body<String>()}")
        }
        return response
    }

    private fun mergeExtraHeaders(
        target: MutableMap<String, List<String>>,
        extraHeaders: Map<String, List<String>>?
    ) {
        extraHeaders?.forEach { (key, value) ->
            val existing = target.getOrDefault(key, listOf())
            val result = mutableListOf<String>()
            result.addAll(existing)
            result.addAll(value)
            target[key] = result
        }
    }
}

fun Headers.asMap(): MutableMap<String, List<String>> {
    val headers = mutableMapOf<String, List<String>>()
    forEach { header, value ->
        headers[header] = value
    }
    return headers
}
