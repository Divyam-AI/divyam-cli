@file:Suppress("unused")

package ai.divyam.data.model

import ai.divyam.client.reflection.Reflectable
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.TextNode
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

    @JsonCreator
    fun fromString(value: String?): ResponseRole? {
        if (value == null) return null
        return try {
            valueOf(value.uppercase(Locale.getDefault()))
        } catch (_: IllegalArgumentException) {
            null
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

sealed interface Input

data class InputText(val value: String) : Input
data class InputMessages(val messages: List<ResponseInputItem>) : Input

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
data class ResponsesRequest(
    @get:JsonProperty("model")
    val model: String,

    // Either a simple string or list of structured input messages
    @get:JsonProperty("input")
    @get:JsonDeserialize(using = InputDeserializer::class)
    @get:JsonSerialize(using = InputSerializer::class)
    val input: Input,

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

@Reflectable
class InputSerializer : JsonSerializer<Input>() {
    override fun serialize(
        value: Input,
        gen: JsonGenerator,
        serializers: SerializerProvider
    ) {
        when (value) {
            is InputText -> gen.writeString(value.value)
            is InputMessages -> {
                gen.writeStartArray()
                value.messages.forEach { gen.writeObject(it) }
                gen.writeEndArray()
            }
        }
    }
}

@Reflectable
class InputDeserializer : JsonDeserializer<Input>() {
    override fun deserialize(
        p: JsonParser,
        ctxt: DeserializationContext
    ): Input {
        val node = p.codec.readTree<com.fasterxml.jackson.databind.JsonNode>(p)
        return when (node) {
            is TextNode -> InputText(node.asText())
            is ArrayNode -> {
                val messages = node.map {
                    val roleText = it["role"]?.asText()
                        ?: ctxt.reportInputMismatch(
                            ResponseInputItem::class.java,
                            "Missing role"
                        )
                    val contentNode = it["content"]
                    val contentValue: Any =
                        if (contentNode.isTextual) contentNode.asText()
                        else p.codec.treeToValue(
                            contentNode,
                            ResponseContentPart::class.java
                        )
                    ResponseInputItem(
                        ResponseRole.USER.fromString(roleText)!!,
                        contentValue
                    )
                }
                InputMessages(messages)
            }

            else -> ctxt.reportInputMismatch(
                Input::class.java,
                "Expected string or array for input"
            )
        }
    }
}

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
    ),
    JsonSubTypes.Type(
        value = ResponseInProgressEvent::class,
        name = "response.in_progress"
    ),
    JsonSubTypes.Type(
        value = ResponseOutputTextDoneEvent::class,
        name = "response.output_text.done"
    ),
    JsonSubTypes.Type(
        value = ResponseRefusalDeltaEvent::class,
        name = "response.refusal.delta"
    ),
    JsonSubTypes.Type(
        value = ResponseOutputToolCallDeltaEvent::class,
        name = "response.output_tool_call.delta"
    ),
    JsonSubTypes.Type(
        value = ResponseOutputToolCallDoneEvent::class,
        name = "response.output_tool_call.done"
    ),
    JsonSubTypes.Type(
        value = ResponseOutputItemAddedEvent::class,
        name = "response.output_item.added"
    ),
    JsonSubTypes.Type(
        value = ResponseContentPartAddedEvent::class,
        name = "response.content_part.added"
    ),
    JsonSubTypes.Type(
        value = ResponseContentPartDoneEvent::class,
        name = "response.content_part.done"
    ),
    JsonSubTypes.Type(
        value = ResponseOutputItemDoneEvent::class,
        name = "response.output_item.done"
    ),
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
 * Emitted as incremental text chunks are streamed from the model.
 *
 * Example payload:
 * {
 *   "type": "response.output_text.delta",
 *   "output_index": 0,
 *   "content_index": 1,
 *   "item_id": "a9fec4ab-f7b4-4abd-8635-6a4a0ebbd12c",
 *   "sequence_number": 4,
 *   "delta": "Hello",
 *   "logprobs": []
 * }
 */
@Reflectable
data class ResponseOutputTextDeltaEvent(
    @get:JsonProperty("type")
    override val type: String = "response.output_text.delta",

    @get:JsonProperty("output_index")
    val outputIndex: Int,

    @get:JsonProperty("content_index")
    val contentIndex: Int,

    @get:JsonProperty("item_id")
    val itemId: String,

    @get:JsonProperty("sequence_number")
    val sequenceNumber: Int? = null,

    // The delta field is a plain string (incremental text chunk)
    @get:JsonProperty("delta")
    val delta: String,

    // Optional token-level probabilities (if requested)
    @get:JsonProperty("logprobs")
    val logprobs: List<Any>? = null
) : ResponseEvent()

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

/**
 * Emitted when a new output item (message/tool/image) is added.
 */
@Reflectable
data class ResponseOutputItemAddedEvent(
    @get:JsonProperty("type")
    override val type: String = "response.output_item.added",

    @get:JsonProperty("item")
    val item: OutputItem
) : ResponseEvent()

/**
 * Emitted when an output item (e.g., a full assistant message, a tool_call, or image call)
 * has finished producing all its content parts.
 *
 * Typical payload:
 * {
 *   "type": "response.output_item.done",
 *   "output_index": 0,
 *   "item_index": 1,
 *   "item_id": "a9fec4ab-f7b4-4abd-8635-6a4a0ebbd12c",
 *   "sequence_number": 7
 * }
 */
@Reflectable
data class ResponseOutputItemDoneEvent(
    @get:JsonProperty("type")
    override val type: String = "response.output_item.done",

    @get:JsonProperty("response_id")
    val responseId: String? = null,

    @get:JsonProperty("output_index")
    val outputIndex: Int,

    @get:JsonProperty("item")
    val item: OutputItem? = null
) : ResponseEvent()

@Reflectable
data class ResponseInProgressEvent(
    @get:JsonProperty("type")
    override val type: String = "response.in_progress",

    @get:JsonProperty("response")
    val response: ResponsesResponse
) : ResponseEvent()

@Reflectable
data class ResponseOutputTextDoneEvent(
    @get:JsonProperty("type")
    override val type: String = "response.output_text.done"
) : ResponseEvent()

@Reflectable
data class ResponseRefusalDeltaEvent(
    @get:JsonProperty("type")
    override val type: String = "response.refusal.delta",

    @get:JsonProperty("delta")
    val delta: String
) : ResponseEvent()

/**
 * Represents one content part (e.g. text, image, tool output).
 */
@Reflectable
data class OutputContentPart(
    @get:JsonProperty("type")
    val type: String,

    @get:JsonProperty("text")
    val text: String? = null,

    @get:JsonProperty("annotations")
    val annotations: List<Any>? = null,

    @get:JsonProperty("logprobs")
    val logprobs: List<Any>? = null
)

/**
 * Emitted when a new content part is added to an existing item.
 *
 * Example payload:
 * {
 *   "type": "response.content_part.added",
 *   "output_index": 0,
 *   "content_index": 0,
 *   "item_id": "aec59c0d-6408-412e-b000-46749ea8931d",
 *   "sequence_number": 3,
 *   "part": { "type": "output_text", "text": "", "annotations": [], "logprobs": [] }
 * }
 */
@Reflectable
data class ResponseContentPartAddedEvent(
    @get:JsonProperty("type")
    override val type: String = "response.content_part.added",

    @get:JsonProperty("output_index")
    val outputIndex: Int,

    @get:JsonProperty("content_index")
    val contentIndex: Int,

    @get:JsonProperty("item_id")
    val itemId: String,

    @get:JsonProperty("sequence_number")
    val sequenceNumber: Int? = null,

    @get:JsonProperty("part")
    val part: OutputContentPart
) : ResponseEvent()

/**
 * Emitted when a content part (e.g., a text segment) has finished generating.
 *
 * Example payload:
 * {
 *   "type": "response.content_part.done",
 *   "output_index": 0,
 *   "content_index": 1,
 *   "item_id": "a9fec4ab-f7b4-4abd-8635-6a4a0ebbd12c",
 *   "sequence_number": 5
 * }
 */
@Reflectable
data class ResponseContentPartDoneEvent(
    @get:JsonProperty("type")
    override val type: String = "response.content_part.done",

    @get:JsonProperty("output_index")
    val outputIndex: Int,

    @get:JsonProperty("content_index")
    val contentIndex: Int,

    @get:JsonProperty("item_id")
    val itemId: String,

    @get:JsonProperty("sequence_number")
    val sequenceNumber: Int? = null
) : ResponseEvent()

@Reflectable
data class ResponseError(
    @get:JsonProperty("message")
    val message: String,

    @get:JsonProperty("code")
    val code: String? = null
)

/**
 * A single function/tool call issued by the model.
 */
@Reflectable
data class ResponseToolCall(
    @get:JsonProperty("id")
    val id: String,

    @get:JsonProperty("type")
    val type: String = "function",

    @get:JsonProperty("name")
    val name: String? = null,

    @get:JsonProperty("arguments")
    val arguments: String? = null
)

/**
 * Partial update (delta) to a tool call — streamed as model generates arguments incrementally.
 */
@Reflectable
data class DeltaToolCall(
    @get:JsonProperty("index")
    val index: Int,

    @get:JsonProperty("id")
    val id: String? = null,

    @get:JsonProperty("type")
    val type: String? = null,

    @get:JsonProperty("name")
    val name: String? = null,

    @get:JsonProperty("arguments")
    val arguments: String? = null
)

/**
 * Emitted as the model streams partial tool call arguments.
 */
@Reflectable
data class ResponseOutputToolCallDeltaEvent(
    @get:JsonProperty("type")
    override val type: String = "response.output_tool_call.delta",

    @get:JsonProperty("delta")
    val delta: DeltaToolCall
) : ResponseEvent()

/**
 * Emitted when a streamed tool call completes.
 */
@Reflectable
data class ResponseOutputToolCallDoneEvent(
    @get:JsonProperty("type")
    override val type: String = "response.output_tool_call.done",

    @get:JsonProperty("output_index")
    val outputIndex: Int,

    @get:JsonProperty("tool_call_index")
    val toolCallIndex: Int
) : ResponseEvent()

@Reflectable
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(value = OutputMessageItem::class, name = "message"),
    JsonSubTypes.Type(value = OutputToolCallItem::class, name = "tool_call"),
    JsonSubTypes.Type(
        value = OutputImageItem::class,
        name = "image_generation_call"
    )
)
sealed class OutputItem {
    @get:JsonProperty("type")
    abstract val type: String
}

/**
 * Assistant message output item (text or multimodal).
 */
@Reflectable
data class OutputMessageItem(
    @get:JsonProperty("type")
    override val type: String = "message",

    @get:JsonProperty("role")
    val role: String = "assistant",

    @get:JsonProperty("content")
    val content: List<MessageContentPart>
) : OutputItem()

@Reflectable
data class MessageContentPart(
    @get:JsonProperty("type")
    val type: String = "output_text",

    @get:JsonProperty("text")
    val text: String
)

/**
 * A model-issued function/tool call.
 */
@Reflectable
data class OutputToolCallItem(
    @get:JsonProperty("type")
    override val type: String = "tool_call",

    @get:JsonProperty("id")
    val id: String,

    @get:JsonProperty("name")
    val name: String,

    @get:JsonProperty("arguments")
    val arguments: String
) : OutputItem()

/**
 * An image generation call output item.
 */
@Reflectable
data class OutputImageItem(
    @get:JsonProperty("type")
    override val type: String = "image_generation_call",

    @get:JsonProperty("image_url")
    val imageUrl: String? = null,

    @get:JsonProperty("image_b64")
    val imageB64: String? = null
) : OutputItem()
