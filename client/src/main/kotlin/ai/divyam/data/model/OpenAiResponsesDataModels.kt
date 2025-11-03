@file:Suppress("unused")

package ai.divyam.data.model

import ai.divyam.client.reflection.Reflectable
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import java.util.Locale

// ---------------------------------------------------------------------------
// Enums / Literals
// ---------------------------------------------------------------------------

@Reflectable
enum class ResponseRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
    DEVELOPER;

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromString(value: String?): ResponseRole? {
            if (value == null) return null
            return try {
                valueOf(value.uppercase(Locale.getDefault()))
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    @JsonValue
    fun toJson(): String = this.name.lowercase(Locale.getDefault())
}

@Reflectable
enum class ReasoningEffort {
    LOW, MEDIUM, HIGH;

    @JsonValue
    fun toJson(): String = this.name.lowercase(Locale.getDefault())
}

// ---------------------------------------------------------------------------
// Request models
// ---------------------------------------------------------------------------

@Reflectable
data class PromptReference(
    @get:JsonProperty("id")
    val id: String,

    @get:JsonProperty("version")
    val version: String? = null,

    @get:JsonProperty("variables")
    val variables: Map<String, Any>? = null
)

@Reflectable
data class ToolDescriptor(
    @get:JsonProperty("type")
    val type: String,

    @get:JsonProperty("name")
    val name: String? = null,

    @get:JsonProperty("description")
    val description: String? = null,

    @get:JsonProperty("parameters")
    val parameters: Map<String, Any>? = null
)

@Reflectable
data class ResponseContentPart(
    @get:JsonProperty("type")
    val type: String = "text",

    @get:JsonProperty("text")
    val text: String
)

@Reflectable
data class ResponseInputItem(
    @get:JsonProperty("role")
    val role: ResponseRole,

    @get:JsonProperty("content")
    val content: Any // String or List<ResponseContentPart>
)

@Reflectable
data class TextFormatSpec(
    @get:JsonProperty("format")
    val format: Map<String, Any>
)

@Reflectable
data class ResponseRequest(
    @get:JsonProperty("model")
    val model: String,

    // Either a simple string or list of structured input messages
    @get:JsonProperty("input")
    val input: Any,

    // Optional conversation/thread continuation
    @get:JsonProperty("previous_response_id")
    val previousResponseId: String? = null,

    // Optional prompt reference (stored prompt)
    @get:JsonProperty("prompt")
    val prompt: PromptReference? = null,

    // Optional tool declarations
    @get:JsonProperty("tools")
    val tools: List<ToolDescriptor>? = null,

    @get:JsonProperty("reasoning")
    val reasoning: Map<String, Any>? = null,

    @get:JsonProperty("max_output_tokens")
    val maxOutputTokens: Int? = null,

    @get:JsonProperty("temperature")
    val temperature: Double? = null,

    @get:JsonProperty("top_p")
    val topP: Double? = null,

    @get:JsonProperty("seed")
    val seed: Int? = null,

    @get:JsonProperty("metadata")
    val metadata: Map<String, Any>? = null,

    @get:JsonProperty("stream")
    val stream: Boolean? = false
)

// ---------------------------------------------------------------------------
// Response models
// ---------------------------------------------------------------------------

@Reflectable
data class ResponseMessageContent(
    @get:JsonProperty("type")
    val type: String = "text",

    @get:JsonProperty("text")
    val text: String
)

@Reflectable
data class ResponseMessage(
    @get:JsonProperty("role")
    val role: ResponseRole,

    @get:JsonProperty("content")
    val content: List<ResponseMessageContent>
)

@Reflectable
data class ResponseOutputItem(
    @get:JsonProperty("role")
    val role: ResponseRole,

    @get:JsonProperty("content")
    val content: List<ResponseMessageContent>
)

@Reflectable
data class ResponseOutput(
    @get:JsonProperty("index")
    val index: Int,

    @get:JsonProperty("output")
    val output: List<ResponseOutputItem>,

    @get:JsonProperty("finish_reason")
    val finishReason: String? = null
)

@Reflectable
data class ResponseUsage(
    @get:JsonProperty("input_tokens")
    val inputTokens: Int,

    @get:JsonProperty("output_tokens")
    val outputTokens: Int,

    @get:JsonProperty("total_tokens")
    val totalTokens: Int
)

@Reflectable
data class ResponsesResponse(
    @get:JsonProperty("id")
    val id: String,

    @get:JsonProperty("object")
    val objectType: String = "response",

    @get:JsonProperty("created")
    val created: Long,

    @get:JsonProperty("model")
    val model: String,

    @get:JsonProperty("output")
    val output: List<ResponseOutputItem>,

    @get:JsonProperty("usage")
    val usage: ResponseUsage? = null,

    @get:JsonProperty("system_fingerprint")
    val systemFingerprint: String? = null,

    @get:JsonProperty("previous_response_id")
    val previousResponseId: String? = null,

    @get:JsonProperty("status")
    val status: String? = null,

    @get:JsonProperty("metadata")
    val metadata: Map<String, Any>? = null
)

// ---------------------------------------------------------------------------
// Response Streaming Events
// Spec: https://api.openai.com/v1/responses (SSE event types)
// ---------------------------------------------------------------------------

@Reflectable
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(
        value = ResponseCreatedEvent::class,
        name = "response.created"
    ),
    JsonSubTypes.Type(
        value = ResponseOutputTextDeltaEvent::class,
        name = "response.output_text.delta"
    ),
    JsonSubTypes.Type(
        value = ResponseImagePartialEvent::class,
        name = "response.image_generation_call.partial_image"
    ),
    JsonSubTypes.Type(
        value = ResponseCompletedEvent::class,
        name = "response.completed"
    ),
    JsonSubTypes.Type(
        value = ResponseErrorEvent::class,
        name = "response.error"
    )
)
sealed class ResponseEvent {
    @get:JsonProperty("type")
    abstract val type: String
}

/**
 * Emitted once when the response object is first created.
 */
@Reflectable
data class ResponseCreatedEvent(
    @get:JsonProperty("type")
    override val type: String = "response.created",

    @get:JsonProperty("response")
    val response: ResponsesResponse
) : ResponseEvent()

/**
 * Emitted as new text is generated by the model.
 * 'delta.text' contains the incremental text chunk.
 */
@Reflectable
data class ResponseOutputTextDeltaEvent(
    @get:JsonProperty("type")
    override val type: String = "response.output_text.delta",

    @get:JsonProperty("delta")
    val delta: DeltaText
) : ResponseEvent()

@Reflectable
data class DeltaText(
    @get:JsonProperty("text")
    val text: String
)

/**
 * Emitted when an image generation call produces a partial image.
 */
@Reflectable
data class ResponseImagePartialEvent(
    @get:JsonProperty("type")
    override val type: String = "response.image_generation_call.partial_image",

    @get:JsonProperty("partial_image_index")
    val partialImageIndex: Int,

    @get:JsonProperty("partial_image_b64")
    val partialImageB64: String
) : ResponseEvent()

/**
 * Emitted when the full response output is available.
 */
@Reflectable
data class ResponseCompletedEvent(
    @get:JsonProperty("type")
    override val type: String = "response.completed",

    @get:JsonProperty("response")
    val response: ResponsesResponse
) : ResponseEvent()

/**
 * Emitted if the response generation failed.
 */
@Reflectable
data class ResponseErrorEvent(
    @get:JsonProperty("type")
    override val type: String = "response.error",

    @get:JsonProperty("error")
    val error: ResponseError
) : ResponseEvent()

@Reflectable
data class ResponseError(
    @get:JsonProperty("message")
    val message: String,

    @get:JsonProperty("code")
    val code: String? = null
)
