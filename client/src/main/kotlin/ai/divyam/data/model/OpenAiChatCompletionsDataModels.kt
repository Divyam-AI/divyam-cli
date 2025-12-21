@file:Suppress("unused")

package ai.divyam.data.model

import ai.divyam.client.reflection.Reflectable
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import java.util.Locale

@Reflectable
enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT;

    @JsonCreator
    fun fromString(value: String?): ChatRole? {
        if (value == null) {
            return null
        }
        return valueOf(value.uppercase(Locale.getDefault()))
    }

    @JsonValue
    fun toLowerCase(): String {
        return this.name.lowercase(Locale.getDefault())
    }

    /**
     * Convert ChatRole → ResponseRole
     */
    fun toResponseRole(): ResponseRole = when (this) {
        SYSTEM -> ResponseRole.SYSTEM
        USER -> ResponseRole.USER
        ASSISTANT -> ResponseRole.ASSISTANT
    }
}

@Reflectable
data class ChatMessage(
    @get:JsonProperty("role")
    val role: ChatRole,

    @get:JsonProperty("content")
    val content: String
)

@Reflectable
data class ChatRequest(
    @get:JsonProperty("model")
    val model: String,
    @get:JsonProperty("messages")
    val messages: List<ChatMessage>,
    @get:JsonProperty("temperature")
    val temperature: Double? = null,
    @get:JsonProperty("top_p")
    val topP: Double? = null,
    @get:JsonProperty("n")
    val n: Int? = null,
    @get:JsonProperty("stream")
    val stream: Boolean? = false,
    @get:JsonProperty("stop")
    val stop: List<String>? = null,
    @get:JsonProperty("max_tokens")
    val maxTokens: Int? = null,
    @get:JsonProperty("presence_penalty")
    val presencePenalty: Double? = null,
    @get:JsonProperty("frequency_penalty")
    val frequencyPenalty: Double? = null,
    @get:JsonProperty("user")
    val user: String? = null
)

@Reflectable
data class ChatCompletionChunk(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String? = null,
    val choices: List<StreamingChoice>
)

@Reflectable
data class Delta(
    val role: String? = null,
    val content: String? = null
)

@Reflectable
data class ChatCompletionResponse(
    val id: String,
    val choices: List<Choice>,
    val created: Long,
    val model: String,

    @get:JsonProperty("object")
    val objectType: String,

    @get:JsonProperty("service_tier")
    val serviceTier: String? = null,

    @get:JsonProperty("system_fingerprint")
    val systemFingerprint: String? = null,

    val usage: Usage? = null
)

@Reflectable
data class ChatCompletionDebugResponse(
    @get:JsonProperty("chat-response")
    val chatResponse: ChatCompletionResponse,

    @get:JsonProperty("response-headers")
    val responseHeaders: Map<String, Any>,
)

@Reflectable
data class ResponsesDebugResponse(
    @get:JsonProperty("chat-response")
    val chatResponse: ResponsesResponse,

    @get:JsonProperty("response-headers")
    val responseHeaders: Map<String, Any>,
)

@Reflectable
data class Choice(
    @get:JsonProperty("finish_reason")
    val finishReason: String? = null,

    val index: Int,
    val logprobs: Any? = null,
    val message: Message
)

@Reflectable
data class StreamingChoice(
    @get:JsonProperty("finish_reason")
    val finishReason: String? = null,

    val index: Int,
    val delta: Delta? = null,
)

@Reflectable
data class Message(
    val content: String,
    val refusal: String? = null,
    val role: String,

    val annotations: Any? = null,
    val audio: Any? = null,

    @get:JsonProperty("function_call")
    val functionCall: Any? = null,

    @get:JsonProperty("tool_calls")
    val toolCalls: Any? = null
)

@Reflectable
data class Usage(
    @get:JsonProperty("completion_tokens")
    val completionTokens: Int,

    @get:JsonProperty("prompt_tokens")
    val promptTokens: Int,

    @get:JsonProperty("total_tokens")
    val totalTokens: Int,

    @get:JsonProperty("completion_tokens_details")
    val completionTokensDetails: Any? = null,

    @get:JsonProperty("prompt_tokens_details")
    val promptTokensDetails: Any? = null
)