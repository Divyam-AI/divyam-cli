package ai.divyam.client

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.formkiq.graalvm.annotations.Reflectable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.InternalSerializationApi
import java.security.cert.X509Certificate
import java.util.Locale
import javax.net.ssl.X509TrustManager

//TODO: Consistent arguments. Take payloads instead of all options! And don't
// call then payloads.
// ------------------- Enums -------------------
@Reflectable
enum class EvalState {
    ACTIVE, INACTIVE;

    @JsonCreator
    fun fromString(value: String?): EvalState? {
        if (value == null) {
            return null
        }
        return EvalState.valueOf(value.uppercase(Locale.getDefault()))
    }
}

@Reflectable
enum class EvalGranularity {
    LLM_REQUEST_RESPONSE,
    TURN_BASED,
    SESSION_BASED;

    @JsonCreator
    fun fromString(value: String?): EvalGranularity? {
        if (value == null) {
            return null
        }
        return EvalGranularity.valueOf(value.uppercase(Locale.getDefault()))
    }
}

@Reflectable
enum class OptimizationGoal {
    HIGH_QUALITY, COST_EFFICIENT;

    @JsonCreator
    fun fromString(value: String?): OptimizationGoal? {
        if (value == null) {
            return null
        }
        return OptimizationGoal.valueOf(value.uppercase(Locale.getDefault()))
    }
}

@Reflectable
enum class ModelAPIAuthMode {
    API_KEYS_SAVED, OAUTH;

    @JsonCreator
    fun fromString(value: String?): ModelAPIAuthMode? {
        if (value == null) {
            return null
        }
        return ModelAPIAuthMode.valueOf(value.uppercase(Locale.getDefault()))
    }
}

@Reflectable
enum class Modality {
    TEXT;

    @JsonCreator
    fun fromString(value: String?): Modality? {
        if (value == null) {
            return null
        }
        return Modality.valueOf(value.uppercase(Locale.getDefault()))
    }

    @JsonValue
    fun toLower(): String {
        return this.name.lowercase(Locale.getDefault())
    }
}

@Reflectable
enum class ModelSelectorState {
    TRAINED,
    SHADOW,
    PROD,
    INACTIVE;

    @JsonCreator
    fun fromString(value: String?): ModelSelectorState? {
        if (value == null) {
            return null
        }
        return ModelSelectorState.valueOf(value.uppercase(Locale.getDefault()))
    }
}

@Reflectable
enum class IpVerificationStrategy {
    IP,
    XFF;

    @JsonCreator
    fun fromString(value: String?): IpVerificationStrategy? {
        if (value == null) {
            return null
        }
        return IpVerificationStrategy.valueOf(value.uppercase(Locale.getDefault()))
    }

    @JsonValue
    fun toLowerCase(): String {
        return this.name.lowercase(Locale.getDefault())
    }
}

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
        return ChatRole.valueOf(value.uppercase(Locale.getDefault()))
    }

    @JsonValue
    fun toLowerCase(): String {
        return this.name.lowercase(Locale.getDefault())
    }
}

// ------------------- Models -------------------
@Reflectable
@JsonIgnoreProperties(ignoreUnknown = true)
data class SecurityPolicy(
    @param:JsonProperty("allowed_ip_networks")
    val allowedIpNetworks: List<String>? = null,

    @param:JsonProperty("blocked_ip_networks")
    val blockedIpNetworks: List<String>? = null,

    @param:JsonProperty("ip_verifications")
    val ipVerifications: List<IpVerificationStrategy>? = null,

    @param:JsonProperty("xff_indices")
    val xffIndices: List<Int>? = null
)

@Reflectable
data class Org(
    val id: Int? = null,
    val name: String,
    @param:JsonProperty("security_policy")
    val securityPolicy: SecurityPolicy? = null
)

@Reflectable
data class OrgUpdateRequest(
    val name: String? = null,
    @param:JsonProperty("security_policy")
    val securityPolicy: SecurityPolicy? = null
)

@Reflectable
data class User(
    val id: Int,

    @param:JsonProperty("email_id")
    val emailId: String,

    val name: String,

    @param:JsonProperty("is_admin")
    val isAdmin: Boolean? = null,

    @param:JsonProperty("is_org_admin")
    val isOrgAdmin: Boolean? = null,

    @param:JsonProperty("org_id")
    val orgId: Int,

    @param:JsonProperty("org_name")
    val orgName: String,

    @param:JsonProperty("security_policy")
    val securityPolicy: SecurityPolicy? = null
)

@Reflectable
data class UserCreateRequest(
    @param:JsonProperty("email_id")
    val emailId: String,

    val name: String,

    @param:JsonProperty("is_admin")
    val isAdmin: Boolean? = null,

    @param:JsonProperty("is_org_admin")
    val isOrgAdmin: Boolean? = null,

    @param:JsonProperty("org_id")
    val orgId: Int,

    @param:JsonProperty("security_policy")
    val securityPolicy: SecurityPolicy? = null,
    val password: String,
)

@Reflectable
data class UserUpdateRequest(
    val name: String? = null,

    @get:JsonProperty("is_org_admin")
    val isOrgAdmin: Boolean? = null,

    @get:JsonProperty("is_admin")
    val isAdmin: Boolean? = null,

    val password: String? = null,

    @get:JsonProperty("security_policy")
    val securityPolicy: SecurityPolicy? = null
)

@Reflectable
data class ServiceAccount(
    val id: String,
    val name: String,
    @get:JsonProperty("org_id")
    val orgId: Int,

    @get:JsonProperty("org_name")
    val orgName: String,

    @get:JsonProperty("api_key")
    val apiKey: String? = null,

    @get:JsonProperty("divyam_auth_key_hashed")
    val divyamAuthKeyHashed: String,

    @get:JsonProperty("optimization_goal")
    val optimizationGoal: OptimizationGoal,

    @get:JsonProperty("authmode_model_api")
    val authModeModelApi: ModelAPIAuthMode,

    @get:JsonProperty("traffic_allocation_config")
    val trafficAllocationConfig: Map<String, Double>,

    @get:JsonProperty("is_org_admin")
    val isOrgAdmin: Boolean? = null,

    @get:JsonProperty("is_admin")
    val isAdmin: Boolean? = null,

    @get:JsonProperty("security_policy")
    val securityPolicy: SecurityPolicy? = null
)

@Reflectable
data class ServiceAccountCreateRequest(
    val name: String,

    @get:JsonProperty("org_id")
    val orgId: Int,

    @get:JsonProperty("optimization_goal")
    val optimizationGoal: OptimizationGoal,

    @get:JsonProperty("authmode_model_api")
    val authModeModelApi: ModelAPIAuthMode,

    @get:JsonProperty("traffic_allocation_config")
    val trafficAllocationConfig: Map<String, Double>? = null,

    @get:JsonProperty("is_org_admin")
    val isOrgAdmin: Boolean,

    @get:JsonProperty("is_admin")
    val isAdmin: Boolean? = null,

    @get:JsonProperty("security_policy")
    val securityPolicy: SecurityPolicy? = null
)

@Reflectable
data class ServiceAccountUpdateRequest(
    val name: String? = null,
    @get:JsonProperty("optimization_goal")
    val optimizationGoal: OptimizationGoal? = null,
    @get:JsonProperty("authmode_model_api")
    val authModeModelApi: ModelAPIAuthMode? = null,
    @get:JsonProperty("traffic_allocation_config")
    val trafficAllocationConfig: Map<String, Double>? = null,
    @get:JsonProperty("is_org_admin")
    val isOrgAdmin: Boolean? = null,
    @get:JsonProperty("is_admin")
    val isAdmin: Boolean? = null,
    @get:JsonProperty("security_policy")
    val securityPolicy: SecurityPolicy? = null,
    @get:JsonProperty("regenerate_api_key")
    val regenerateApiKey: Boolean? = null
)

@Reflectable
data class TextPricing(val input: Double, var output: Double)

@Reflectable
data class ModelProviderInfo(
    val id: Int,
    @get:JsonProperty("name_provider")
    val nameProvider: String,
    @get:JsonProperty("id_provider")
    val idProvider: Int,
    @get:JsonProperty("name_model")
    val nameModel: String,
    @get:JsonProperty("id_model")
    val idModel: Int,
    val endpoint: String,
    @get:JsonProperty("masked_api_key_model")
    val maskedApiKeyModel: String,
    @get:JsonProperty("service_account_id")
    val serviceAccountId: String?,
    @get:JsonProperty("org_name")
    val orgName: String? = null,
    @get:JsonProperty("configs_model")
    val configsModel: String,
    @get:JsonProperty("supported_modalities")
    val supportedModalities: List<Modality> = listOf(Modality.TEXT),
    @get:JsonProperty("text_pricing")
    val textPricing: TextPricing,
    val currency: String? = null,
    @get:JsonProperty("per_n_tokens")
    val perNTokens: Int? = null
)

@Reflectable
data class ModelProviderInfoCreation(
    @get:JsonProperty("service_account_id")
    val serviceAccountId: String? = null,

    // TODO: some places name_provider, some places provider_name.
    @get:JsonProperty("name_provider")
    val nameProvider: String,

    // TODO: some places name_mode, some places model_name.
    @get:JsonProperty("name_model")
    val nameModel: String,
    val endpoint: String,
    @get:JsonProperty("api_key_model")
    val apiKeyModel: String,
    @get:JsonProperty("configs_model")
    val configsModel: String,
    @get:JsonProperty("supported_modalities")
    val supportedModalities: List<Modality> = listOf(Modality.TEXT),
    @get:JsonProperty("text_pricing")
    val textPricing: TextPricing,
    val currency: String? = null,
    @get:JsonProperty("per_n_tokens")
    val perNTokens: Int? = null
)

@Reflectable
data class ModelProviderInfoUpdation(
    @get:JsonProperty("service_account_id")
    val serviceAccountId: String? = null,
    @get:JsonProperty("provider_name")
    val providerName: String? = null,
    @get:JsonProperty("model_name")
    val modelName: String? = null,
    val endpoint: String? = null,
    @get:JsonProperty("api_key_model")
    val apiKeyModel: String? = null,
    @get:JsonProperty("configs_model")
    val configsModel: String? = null,
    @get:JsonProperty("supported_modalities")
    val supportedModalities: List<Modality>? = null,
    @get:JsonProperty("text_pricing")
    val textPricing: TextPricing? = null,
    val currency: String? = null,
    @get:JsonProperty("per_n_tokens")
    val perNTokens: Int? = null
)

@Reflectable
data class ModelSelector(
    val id: Int,
    val name: String,
    @get:JsonProperty("org_id")
    val orgId: Int,
    @get:JsonProperty("service_account_id")
    val serviceAccountId: String? = null,
    val state: ModelSelectorState,
    val endpoint: String? = null,
    @get:JsonProperty("created_at")
    val createdAt: Long
)

@Reflectable
data class ModelSelectorCreateRequest(
    val name: String,
    @get:JsonProperty("org_id")
    val orgId: Int,
    @get:JsonProperty("service_account_id")
    val serviceAccountId: String? = null,
    val state: ModelSelectorState = ModelSelectorState.TRAINED,
    val endpoint: String? = null
)

@Reflectable
data class ModelSelectorUpdateRequest(
    val name: String? = null,
    val state: ModelSelectorState? = null,
    val endpoint: String? = null
)

@Reflectable
data class Eval(
    val id: Int,
    @get:JsonProperty("org_id")
    val orgId: Int,
    @get:JsonProperty("service_account_id")
    val serviceAccountId: String,
    val name: String,
    val granularity: EvalGranularity,
    @get:JsonProperty("class_name")
    val className: String,
    @get:JsonProperty("class_init_config")
    val classInitConfig: Map<String, Any> = emptyMap(),
    val state: EvalState,
    @get:JsonProperty("created_at")
    val createdAt: Int
)

@Reflectable
data class EvalCreateRequest(
    @get:JsonProperty("org_id")
    val orgId: Int,
    @get:JsonProperty("service_account_id")
    val serviceAccountId: String,
    val name: String,
    val granularity: EvalGranularity,
    @get:JsonProperty("class_name")
    val className: String,
    @get:JsonProperty("class_init_config")
    val classInitConfig: Map<String, Any> = emptyMap(),
    val state: EvalState
)

// TODO: Only allow state change.
@Reflectable
data class EvalUpdateRequest(
    val name: String? = null,
    val granularity: EvalGranularity? = null,
    @get:JsonProperty("class_name")
    val className: String? = null,
    @get:JsonProperty("class_init_config")
    val classInitConfig: Map<String, Any>? = null,
    val state: EvalState? = null
)

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
    val stream: Boolean? = null,
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

    val usage: Usage
)

@Reflectable
data class ChatCompletionDebugResponse(
    @get:JsonProperty("chat-response")
    val chatResponse: ChatCompletionResponse,

    @get:JsonProperty("response-headers")
    val responseHeaders: Map<String, Any>,
)

@Reflectable
data class Choice(
    @get:JsonProperty("finish_reason")
    val finishReason: String,

    val index: Int,
    val logprobs: Any? = null,
    val message: Message
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

// ------------------- Client -------------------
class DivyamClient(
    emailId: String? = null,
    password: String? = null,
    private val endpoint: String = "https://api.divyam.ai",
    apiToken: String? = null,
    /**
     * Non-production only
     */
    private val disableTlsVerification: Boolean = false
) {
    private val client = HttpClient(CIO) {
        engine {
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

        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                @Suppress("DEPRECATION")
                enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            }
        }
    }

    private val apiToken: String = apiToken ?: run {
        require(emailId != null && password != null) {
            "Email ID and password must be provided if API token is not set."
        }
        runBlocking {
            getBearerToken(emailId, password)
        }
    }

    private suspend fun getBearerToken(
        emailId: String,
        password: String
    ): String {
        val httpResponse = client.post("$endpoint/token") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("email_id" to emailId, "password" to password))
        }
        if (httpResponse.status != HttpStatusCode.OK) {
            error(
                "Error fetching bearer token - ${
                    httpResponse.body<String>
                        ()
                }"
            )
        }
        val response: Map<String, String> = httpResponse.body()
        return response["bearer_token"]
            ?: error("Bearer token missing in response")
    }

    private fun headers() =
        headersOf("Authorization" to listOf("Bearer $apiToken"))

    fun generateRandomString(prefix: String = "", length: Int = 6): String {
        val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return prefix + (1..length).map { chars.random() }.joinToString("")
    }

    // ---------------- Orgs ----------------
    suspend fun getOrgById(orgId: Int): Org {
        val httpResponse =
            client.get("$endpoint/v1/orgs/$orgId") { headers.appendAll(headers()) }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error getting org by ID: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun listOrgs(): List<Org> {
        val httpResponse = client.get("$endpoint/v1/orgs/") {
            headers.appendAll(
                headers
                    ()
            )
        }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error listing orgs: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    suspend fun getOrg(name: String): Org {
        val httpResponse =
            client.get("$endpoint/v1/orgs/") { headers.appendAll(headers()) }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error getting org: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        val orgs: List<Org> = httpResponse.body()
        return orgs.find { it.name.contains(name, ignoreCase = true) }
            ?: error("Org with name '$name' not found")
    }

    suspend fun createOrg(
        name: String,
        securityPolicy: SecurityPolicy? = null
    ): Org {
        val payload = Org(name = name, securityPolicy = securityPolicy)
        val httpResponse = client.post("$endpoint/v1/orgs/") {
            headers.appendAll(headers())
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error creating org: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    suspend fun updateOrg(
        orgId: Int,
        name: String?,
        securityPolicy: SecurityPolicy?
    ): Org {
        val payload = OrgUpdateRequest(name, securityPolicy)
        val httpResponse = client.patch("$endpoint/v1/orgs/$orgId") {
            headers.appendAll(headers())
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error updating org: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    // ---------------- Users ----------------
    suspend fun getUsers(orgId: Int): List<User> {
        val httpResponse = client.get("$endpoint/v1/orgs/$orgId/users/") {
            headers.appendAll(
                headers()
            )
        }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error getting users for org ID $orgId: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    suspend fun createUser(
        orgId: Int, password: String, emailId: String, name: String? = null,
        isOrgAdmin: Boolean = false, isAdmin: Boolean = false,
        securityPolicy: SecurityPolicy? = null
    ): User {
        val finalName = name ?: generateRandomString(
            prefix = if (isAdmin) "admin_" else if (isOrgAdmin) "org_admin_" else "org_user_"
        )
        val payload = UserCreateRequest(
            emailId = emailId,
            name = finalName,
            password = password,
            orgId = orgId,
            isOrgAdmin = isOrgAdmin,
            isAdmin =
                isAdmin,
            securityPolicy = securityPolicy
        )
        val httpResponse = client.post("$endpoint/v1/orgs/$orgId/users/") {
            headers.appendAll(headers())
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error creating user: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    suspend fun getUser(emailId: String): User {
        val httpResponse = client.get("$endpoint/v1/users/$emailId/") {
            headers.appendAll(headers())
        }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error getting user with email $emailId: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    suspend fun updateUser(
        emailId: String,
        updateRequest: UserUpdateRequest
    ): User {
        val httpResponse = client.post("$endpoint/v1/users/$emailId/") {
            headers.appendAll(headers())
            contentType(ContentType.Application.Json)
            setBody(updateRequest)
        }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error updating user with email $emailId: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    // ---------------- Service Accounts ----------------
    suspend fun getServiceAccounts(orgId: Int): List<ServiceAccount> {
        val httpResponse =
            client.get("$endpoint/v1/orgs/$orgId/service_accounts/") {
                headers.appendAll(
                    headers()
                )
            }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error getting service accounts for org ID $orgId: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    suspend fun getServiceAccount(serviceAccountId: String): ServiceAccount {
        val httpResponse =
            client.get("$endpoint/v1/service_accounts/$serviceAccountId/") {
                headers.appendAll(
                    headers()
                )
            }

        if (httpResponse.status != HttpStatusCode.OK) {
            error(
                "Error reading service account ${
                    httpResponse.body<String>
                        ()
                }"
            )
        }
        return httpResponse.body()
    }

    suspend fun createServiceAccount(
        orgId: Int, name: String? = null,
        isOrgAdmin: Boolean = false, isAdmin: Boolean = false,
        authModeModelApi: ModelAPIAuthMode = ModelAPIAuthMode.API_KEYS_SAVED,
        trafficAllocationConfig: Map<String, Double>? = null,
        optimizationGoal: OptimizationGoal = OptimizationGoal.HIGH_QUALITY,
        securityPolicy: SecurityPolicy? = null
    ): ServiceAccount {
        val finalName = name ?: generateRandomString(
            prefix = if (isAdmin) "admin_" else if (isOrgAdmin) "org_admin_" else "org_user_sa_"
        )
        trafficAllocationConfig ?: mapOf(
            "control" to 10.0,
            "selector_disabled" to 90.0
        )
        val payload = ServiceAccountCreateRequest(
            orgId = orgId,
            name = finalName,
            optimizationGoal = optimizationGoal,
            authModeModelApi = authModeModelApi,
            trafficAllocationConfig = trafficAllocationConfig,
            isOrgAdmin = isOrgAdmin,
            isAdmin =
                isAdmin,
            securityPolicy = securityPolicy
        )
        val httpResponse =
            client.post("$endpoint/v1/orgs/$orgId/service_accounts/") {
                headers.appendAll(headers())
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error creating service account: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    suspend fun updateServiceAccount(
        serviceAccountId: String,
        req: ServiceAccountUpdateRequest
    ): ServiceAccount {
        val httpResponse =
            client.post("$endpoint/v1/service_accounts/$serviceAccountId/") {
                headers.appendAll(headers())
                contentType(ContentType.Application.Json)
                setBody(req)
            }

        if (httpResponse.status != HttpStatusCode.OK) {
            error(
                "Error updating service account - ${
                    httpResponse.body<String>
                        ()
                }"
            )
        }
        return httpResponse.body()
    }

    // ---------------- Model Infos ----------------
    suspend fun getModelInfos(
        orgId: Int,
        serviceAccountId: String?
    ): List<ModelProviderInfo> {
        val httpResponse = client.get("$endpoint/v1/orgs/$orgId/models_info/") {
            headers.appendAll(headers())
            if (serviceAccountId != null) {
                parameter("service_account_id", serviceAccountId)
            }
        }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error getting model infos: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    suspend fun getModelInfo(orgId: Int, modelInfoId: Int): ModelProviderInfo {
        // TODO: Rest resource structure check.
        val httpResponse =
            client.get("$endpoint/v1/orgs/$orgId/models_info/$modelInfoId/") {
                headers.appendAll(headers())
            }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error getting model info: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    suspend fun createModelInfo(
        orgId: Int,
        providerInfoCreation: ModelProviderInfoCreation
    ): ModelProviderInfo {
        val httpResponse =
            client.post("$endpoint/v1/orgs/$orgId/models_info/") {
                headers.appendAll(headers())
                contentType(ContentType.Application.Json)
                setBody(providerInfoCreation)
            }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error creating model info: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    suspend fun updateModelInfo(
        orgId: Int,
        modelInfoId: Int,
        providerInfoUpdation: ModelProviderInfoUpdation
    ): ModelProviderInfo {
        val httpResponse =
            client.post("$endpoint/v1/orgs/$orgId/models_info/$modelInfoId/") {
                headers.appendAll(headers())
                contentType(ContentType.Application.Json)
                setBody(providerInfoUpdation)
            }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error updating model info: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    // ---------------- Model Selectors ----------------
    suspend fun createModelSelector(selectorCreateRequest: ModelSelectorCreateRequest): ModelSelector {
        val httpResponse = client.post("$endpoint/v1/model_selectors/") {
            headers.appendAll(headers())
            contentType(ContentType.Application.Json)
            setBody(selectorCreateRequest)
        }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error creating model selector: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    suspend fun updateModelSelector(
        id: String,
        selectorUpdateRequest: ModelSelectorUpdateRequest
    ): ModelSelector {
        val httpResponse = client.post("$endpoint/v1/model_selectors/$id/") {
            headers.appendAll(headers())
            contentType(ContentType.Application.Json)
            setBody(selectorUpdateRequest)
        }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error updating model selector: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    suspend fun getModelSelectors(
        orgId: Int, serviceAccountId: String? = null,
        states: List<ModelSelectorState>? = listOf(ModelSelectorState.PROD)
    ): List<ModelSelector> {
        val httpResponse = client.get("$endpoint/v1/model_selectors/") {
            headers.appendAll(headers())
            parameter("org_id", orgId)
            if (serviceAccountId != null) parameter(
                "service_account_id",
                serviceAccountId
            )
            states?.forEach { parameter("states", it) }
        }
        if (httpResponse.status == HttpStatusCode.NotFound) {
            return emptyList()
        }

        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error getting model selectors: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    // ---------------- Evals ----------------
    suspend fun createEval(
        serviceAccountId: String,
        createRequest: EvalCreateRequest
    ): Eval {
        val httpResponse =
            client.post("$endpoint/v1/service_accounts/$serviceAccountId/evals/") {
                headers.appendAll(headers())
                contentType(ContentType.Application.Json)
                setBody(createRequest)
            }
        // TODO: No error message coming from server.
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error creating eval: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    suspend fun getEval(serviceAccountId: String, evalId: Int): Eval {
        val httpResponse =
            client.get("$endpoint/v1/service_accounts/$serviceAccountId/evals/$evalId/") {
                headers.appendAll(headers())
            }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error getting eval: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    suspend fun listEvals(
        serviceAccountId: String,
        states: List<EvalState>? = null
    ): List<Eval> {
        val httpResponse =
            client.get("$endpoint/v1/service_accounts/$serviceAccountId/evals/") {
                headers.appendAll(headers())
                states?.forEach { state ->
                    parameter("states", state)
                }
            }

        if (httpResponse.status == HttpStatusCode.NotFound) {
            // TODO: change server to return empty list.
            return emptyList()
        }

        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error listing evals: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    suspend fun updateEval(
        serviceAccountId: String,
        evalId: Int,
        updateRequest: EvalUpdateRequest
    ): Eval {
        val httpResponse =
            client.post("$endpoint/v1/service_accounts/$serviceAccountId/evals/$evalId/") {
                headers.appendAll(headers())
                contentType(ContentType.Application.Json)
                setBody(updateRequest)
            }
        if (httpResponse.status != HttpStatusCode.OK) {
            error("Error updating eval: ${httpResponse.status} - ${httpResponse.body<String>()}")
        }
        return httpResponse.body()
    }

    // ---------------- Chat ----------------
    suspend fun chatCompletion(
        chatRequest: ChatRequest, mockSelector:
        Boolean = false, mockModel: Boolean = false
    ): ChatCompletionResponse {
        val maxRetries = 3
        var retries = maxRetries
        var response: HttpResponse? = null

        while (retries > 0) {
            response = client.post("$endpoint/v1/chat/completions") {
                headers.appendAll(headers())
                parameter("mock_selector", mockSelector.toString())
                parameter("mock_model", mockModel.toString())
                contentType(ContentType.Application.Json)
                setBody(chatRequest)
            }

            if (response.status.value == 200) {
                return response.body()
            }

            // TODO: logging
            // logger.info("Failed completions response: ${response.status}")
            retries--
            if (retries > 0) {
                delay(1000) // wait before retrying
            }
        }

        error(
            "Error getting response: ${response!!.status} - ${
                response.body<String>()
            }"
        )
    }

    suspend fun chatCompletionDebugMode(
        chatRequest: ChatRequest, mockSelector:
        Boolean = false, mockModel: Boolean = false
    ): ChatCompletionDebugResponse {
        val maxRetries = 3
        var retries = maxRetries
        var response: HttpResponse? = null

        while (retries > 0) {
            response = client.post("$endpoint/v1/chat/completions") {
                headers.appendAll(headers())
                parameter("mock_selector", mockSelector.toString())
                parameter("mock_model", mockModel.toString())
                contentType(ContentType.Application.Json)
                setBody(chatRequest)
            }

            if (response.status.value == 200) {
                val chatCompletionResponse: ChatCompletionResponse =
                    response.body()
                val debugResponse = ChatCompletionDebugResponse(
                    chatCompletionResponse,
                    response.headers.asMap()
                )
                return debugResponse
            }

            // TODO: logging
            // logger.info("Failed completions response: ${response.status}")
            retries--
            if (retries > 0) {
                delay(1000) // wait before retrying
            }
        }

        error(
            "Error getting response: ${response!!.status} - ${
                response.body<String>()
            }"
        )
    }

    suspend fun chatCompletionPayloadMode(
        payload: Any, mockSelector:
        Boolean = false, mockModel: Boolean = false
    ): HttpResponse {
        val maxRetries = 3
        var retries = maxRetries
        var response: HttpResponse? = null

        while (retries > 0) {
            response = client.post("$endpoint/v1/chat/completions") {
                headers.appendAll(headers())
                parameter("mock_selector", mockSelector.toString())
                parameter("mock_model", mockModel.toString())
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            if (response.status.value == 200) {
                return response
            }

            // TODO: logging
            // logger.info("Failed completions response: ${response.status}")
            retries--
            if (retries > 0) {
                delay(1000) // wait before retrying
            }
        }

        return response!!
    }
}

fun Headers.asMap(): MutableMap<String, Any> {
    val headers = mutableMapOf<String, Any>()
    forEach { header, value ->
        headers[header] = value
    }
    return headers
}