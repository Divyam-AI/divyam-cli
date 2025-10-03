@file:Suppress("unused")

package ai.divyam.client.data.models

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.formkiq.graalvm.annotations.Reflectable
import java.util.Locale

@Reflectable
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
data class HumanAccessBearerToken(

    @param:JsonProperty("bearer_token")
    val bearerToken: String,

    @param:JsonProperty("name")
    val name: String
)

@Reflectable
data class ModelProviderInfo(

    @param:JsonProperty("id")
    val id: Int,

    @param:JsonProperty("name_provider")
    val nameProvider: String,

    @param:JsonProperty("id_provider")
    val idProvider: Int,

    @param:JsonProperty("name_model")
    val nameModel: String,

    @param:JsonProperty("id_model")
    val idModel: Int,

    @param:JsonProperty("endpoint")
    val endpoint: String,

    @param:JsonProperty("masked_api_key_model")
    val maskedApiKeyModel: String,

    @param:JsonProperty("service_account_id")
    val serviceAccountId: String,

    @param:JsonProperty("org_id")
    val orgId: Int? = null,

    @param:JsonProperty("org_name")
    val orgName: String? = null,

    @param:JsonProperty("configs_model")
    val configsModel: String,

    @param:JsonProperty("supported_modalities")
    val supportedModalities: List<Modality>,

    @param:JsonProperty("text_pricing")
    val textPricing: TextPricing,

    @param:JsonProperty("currency")
    val currency: String? = null,

    @param:JsonProperty("per_n_tokens")
    val perNTokens: Int? = null,

    @param:JsonProperty("is_active")
    val isActive: Boolean,

    @param:JsonProperty("is_selection_enabled")
    val isSelectionEnabled: Boolean
)


@Reflectable
data class ModelProviderInfoCreation(

    @param:JsonProperty("service_account_id")
    val serviceAccountId: String? = null,

    @param:JsonProperty("name_provider")
    val nameProvider: String,

    @param:JsonProperty("name_model")
    val nameModel: String,

    @param:JsonProperty("endpoint")
    val endpoint: String,

    @param:JsonProperty("api_key_model")
    val apiKeyModel: String,

    @param:JsonProperty("configs_model")
    val configsModel: String,

    @param:JsonProperty("supported_modalities")
    val supportedModalities: List<Modality>,

    @param:JsonProperty("text_pricing")
    val textPricing: TextPricing,

    @param:JsonProperty("currency")
    val currency: String? = null,

    @param:JsonProperty("per_n_tokens")
    val perNTokens: Int? = null,

    @param:JsonProperty("is_selection_enabled")
    val isSelectionEnabled: Boolean? = null
)


@Reflectable
data class ModelProviderInfoUpdation(

    @param:JsonProperty("service_account_id")
    val serviceAccountId: String? = null,

    @param:JsonProperty("provider_name")
    val providerName: String? = null,

    @param:JsonProperty("model_name")
    val modelName: String? = null,

    @param:JsonProperty("endpoint")
    val endpoint: String? = null,

    @param:JsonProperty("api_key_model")
    val apiKeyModel: String? = null,

    @param:JsonProperty("configs_model")
    val configsModel: String? = null,

    @param:JsonProperty("supported_modalities")
    val supportedModalities: List<Modality>? = null,

    @param:JsonProperty("text_pricing")
    val textPricing: TextPricing? = null,

    @param:JsonProperty("currency")
    val currency: String? = null,

    @param:JsonProperty("per_n_tokens")
    val perNTokens: Int? = null,

    @param:JsonProperty("is_active")
    val isActive: Boolean? = null,

    @param:JsonProperty("is_selection_enabled")
    val isSelectionEnabled: Boolean? = null
)

@Reflectable
data class Eval(

    @param:JsonProperty("id")
    val id: Int,

    @param:JsonProperty("org_id")
    val orgId: Int,

    @param:JsonProperty("service_account_id")
    val serviceAccountId: String,

    @param:JsonProperty("name")
    val name: String,

    @param:JsonProperty("granularity")
    val granularity: EvalGranularity,

    @param:JsonProperty("class_name")
    val className: String,

    @param:JsonProperty("class_init_config")
    val classInitConfig: Map<String, Any>,

    @param:JsonProperty("sampling_config")
    val samplingConfig: Map<String, Any>? = null,

    @param:JsonProperty("state")
    val state: EvalState,

    @param:JsonProperty("created_at")
    val createdAt: Int
)


@Reflectable
data class EvalCreateRequest(

    @param:JsonProperty("org_id")
    val orgId: Int,

    @param:JsonProperty("service_account_id")
    val serviceAccountId: String,

    @param:JsonProperty("name")
    val name: String,

    @param:JsonProperty("granularity")
    val granularity: EvalGranularity,

    @param:JsonProperty("class_name")
    val className: String,

    @param:JsonProperty("class_init_config")
    val classInitConfig: Map<String, Any>,

    @param:JsonProperty("sampling_config")
    val samplingConfig: Map<String, Any>? = null,

    @param:JsonProperty("state")
    val state: EvalState
)


@Reflectable
data class EvalUpdateRequest(

    @param:JsonProperty("name")
    val name: String? = null,

    @param:JsonProperty("granularity")
    val granularity: EvalGranularity? = null,

    @param:JsonProperty("class_name")
    val className: String? = null,

    @param:JsonProperty("class_init_config")
    val classInitConfig: Map<String, Any>? = null,

    @param:JsonProperty("state")
    val state: EvalState? = null,

    @param:JsonProperty("sampling_config")
    val samplingConfig: Map<String, Any>? = null
)

@Reflectable
enum class ModelSelectorState {
    TRAINED, SHADOW, PROD, INACTIVE;

    @JsonCreator
    fun fromString(value: String?): ModelSelectorState? {
        if (value == null) {
            return null
        }
        return ModelSelectorState.valueOf(value.replace("-", "_").uppercase(
            Locale.getDefault()))
    }

    private val valueMap = mapOf(
        "TRAINED" to "TRAINED",
        "SHADOW" to "SHADOW",
        "PROD" to "PROD",
        "INACTIVE" to "INACTIVE"
    )

    @JsonValue
    fun toValue(): String {
         return valueMap[this.name]!!
    }
}

@Reflectable
data class ModelSelector(

    @param:JsonProperty("id")
    val id: Int,

    @param:JsonProperty("name")
    val name: String,

    @param:JsonProperty("org_id")
    val orgId: Int,

    @param:JsonProperty("service_account_id")
    val serviceAccountId: String? = null,

    @param:JsonProperty("state")
    val state: ModelSelectorState,

    @param:JsonProperty("endpoint")
    val endpoint: String? = null,

    @param:JsonProperty("created_at")
    val createdAt: Int
)


@Reflectable
data class ModelSelectorCreateRequest(

    @param:JsonProperty("name")
    val name: String,

    @param:JsonProperty("org_id")
    val orgId: Int,

    @param:JsonProperty("service_account_id")
    val serviceAccountId: String? = null,

    @param:JsonProperty("state")
    val state: ModelSelectorState,

    @param:JsonProperty("endpoint")
    val endpoint: String? = null
)


@Reflectable
data class ModelSelectorUpdateRequest(

    @param:JsonProperty("name")
    val name: String? = null,

    @param:JsonProperty("state")
    val state: ModelSelectorState? = null,

    @param:JsonProperty("endpoint")
    val endpoint: String? = null
)

@Reflectable
data class Org(

    @param:JsonProperty("id")
    val id: Int? = null,

    @param:JsonProperty("name")
    val name: String,

    @param:JsonProperty("security_policy")
    val securityPolicy: SecurityPolicy? = null
)


@Reflectable
data class OrgUpdateRequest(

    @param:JsonProperty("name")
    val name: String? = null,

    @param:JsonProperty("security_policy")
    val securityPolicy: SecurityPolicy? = null
)

@Reflectable
data class ServiceAccount(

    @param:JsonProperty("id")
    val id: String,

    @param:JsonProperty("name")
    val name: String,

    @param:JsonProperty("org_id")
    val orgId: Int,

    @param:JsonProperty("org_name")
    val orgName: String,

    @param:JsonProperty("api_key")
    val apiKey: String? = null,

    @param:JsonProperty("divyam_auth_key_hashed")
    val divyamAuthKeyHashed: String,

    @param:JsonProperty("optimization_goal")
    val optimizationGoal: OptimizationGoal,

    @param:JsonProperty("authmode_model_api")
    val authmodeModelApi: ModelAPIAuthMode,

    @param:JsonProperty("traffic_allocation_config")
    val trafficAllocationConfig: Map<String, Double>,

    @param:JsonProperty("is_org_admin")
    val isOrgAdmin: Boolean? = null,

    @param:JsonProperty("is_admin")
    val isAdmin: Boolean? = null,

    @param:JsonProperty("security_policy")
    val securityPolicy: SecurityPolicy? = null
)


@Reflectable
data class ServiceAccountCreateRequest(

    @param:JsonProperty("name")
    val name: String,

    @param:JsonProperty("org_id")
    val orgId: Int,

    @param:JsonProperty("optimization_goal")
    val optimizationGoal: OptimizationGoal,

    @param:JsonProperty("authmode_model_api")
    val authmodeModelApi: ModelAPIAuthMode,

    @param:JsonProperty("traffic_allocation_config")
    val trafficAllocationConfig: Map<String, Double>,

    @param:JsonProperty("is_org_admin")
    val isOrgAdmin: Boolean,

    @param:JsonProperty("is_admin")
    val isAdmin: Boolean? = null,

    @param:JsonProperty("security_policy")
    val securityPolicy: SecurityPolicy? = null
)


@Reflectable
data class ServiceAccountUpdateRequest(

    @param:JsonProperty("name")
    val name: String? = null,

    @param:JsonProperty("optimization_goal")
    val optimizationGoal: OptimizationGoal? = null,

    @param:JsonProperty("authmode_model_api")
    val authmodeModelApi: ModelAPIAuthMode? = null,

    @param:JsonProperty("traffic_allocation_config")
    val trafficAllocationConfig: Map<String, Double>? = null,

    @param:JsonProperty("is_org_admin")
    val isOrgAdmin: Boolean? = null,

    @param:JsonProperty("is_admin")
    val isAdmin: Boolean? = null,

    @param:JsonProperty("security_policy")
    val securityPolicy: SecurityPolicy? = null,

    @param:JsonProperty("regenerate_api_key")
    val regenerateApiKey: Boolean? = null
)

@Reflectable
data class User(

    @param:JsonProperty("email_id")
    val emailId: String,

    @param:JsonProperty("name")
    val name: String,

    @param:JsonProperty("is_org_admin")
    val isOrgAdmin: Boolean? = null,

    @param:JsonProperty("is_admin")
    val isAdmin: Boolean? = null,

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

    @param:JsonProperty("name")
    val name: String,

    @param:JsonProperty("org_id")
    val orgId: Int,

    @param:JsonProperty("is_org_admin")
    val isOrgAdmin: Boolean,

    @param:JsonProperty("is_admin")
    val isAdmin: Boolean? = null,

    @param:JsonProperty("password")
    val password: String,

    @param:JsonProperty("security_policy")
    val securityPolicy: SecurityPolicy? = null
)


@Reflectable
data class UserUpdateRequest(

    @param:JsonProperty("name")
    val name: String? = null,

    @param:JsonProperty("is_org_admin")
    val isOrgAdmin: Boolean? = null,

    @param:JsonProperty("is_admin")
    val isAdmin: Boolean? = null,

    @param:JsonProperty("password")
    val password: String? = null,

    @param:JsonProperty("security_policy")
    val securityPolicy: SecurityPolicy? = null
)

@Reflectable
data class CustomTags(

    @param:JsonProperty("tags_dict")
    val tagsDict: Map<String, Any>? = null
)


@Reflectable
enum class Modality {
    TEXT;

    @JsonCreator
    fun fromString(value: String?): Modality? {
        if (value == null) {
            return null
        }
        return Modality.valueOf(value.replace("-", "_").uppercase(
            Locale.getDefault()))
    }

    private val valueMap = mapOf(
        "TEXT" to "text"
    )

    @JsonValue
    fun toValue(): String {
         return valueMap[this.name]!!
    }
}

@Reflectable
data class TextPricing(

    @param:JsonProperty("input")
    val input: Double,

    @param:JsonProperty("output")
    val output: Double
)


@Reflectable
data class ChatCompletionMetering(

    @param:JsonProperty("response_id")
    val responseId: String,

    @param:JsonProperty("stream")
    val stream: Boolean,

    @param:JsonProperty("chunk_count")
    val chunkCount: Int,

    @param:JsonProperty("ttft_ms")
    val ttftMs: Int,

    @param:JsonProperty("ttlt_ms")
    val ttltMs: Int,

    @param:JsonProperty("prompt_tokens")
    val promptTokens: Int,

    @param:JsonProperty("completion_tokens")
    val completionTokens: Int,

    @param:JsonProperty("total_tokens")
    val totalTokens: Int,

    @param:JsonProperty("provider_metering_raw")
    val providerMeteringRaw: Map<String, Any>
)


@Reflectable
data class DecryptedModelProviderInfo(

    @param:JsonProperty("id")
    val id: Int,

    @param:JsonProperty("provider")
    val provider: String,

    @param:JsonProperty("model")
    val model: String,

    @param:JsonProperty("api_key")
    val apiKey: String,

    @param:JsonProperty("endpoint")
    val endpoint: String,

    @param:JsonProperty("supported_modalities")
    val supportedModalities: List<String>,

    @param:JsonProperty("pricing")
    val pricing: TextPricing,

    @param:JsonProperty("currency")
    val currency: String,

    @param:JsonProperty("per_n_tokens")
    val perNTokens: Int
)

@Reflectable
enum class ModelAPIAuthMode {
    API_KEYS_IN_HEADER, API_KEYS_SAVED;

    @JsonCreator
    fun fromString(value: String?): ModelAPIAuthMode? {
        if (value == null) {
            return null
        }
        return ModelAPIAuthMode.valueOf(value.replace("-", "_").uppercase(
            Locale.getDefault()))
    }

    private val valueMap = mapOf(
        "API_KEYS_IN_HEADER" to "API_KEYS_IN_HEADER",
        "API_KEYS_SAVED" to "API_KEYS_SAVED"
    )

    @JsonValue
    fun toValue(): String {
         return valueMap[this.name]!!
    }
}

@Reflectable
enum class OptimizationGoal {
    COST_EFFICIENT, HIGH_QUALITY;

    @JsonCreator
    fun fromString(value: String?): OptimizationGoal? {
        if (value == null) {
            return null
        }
        return OptimizationGoal.valueOf(value.replace("-", "_").uppercase(
            Locale.getDefault()))
    }

    private val valueMap = mapOf(
        "COST_EFFICIENT" to "COST_EFFICIENT",
        "HIGH_QUALITY" to "HIGH_QUALITY"
    )

    @JsonValue
    fun toValue(): String {
         return valueMap[this.name]!!
    }
}

@Reflectable
enum class EvalState {
    ACTIVE, INACTIVE;

    @JsonCreator
    fun fromString(value: String?): EvalState? {
        if (value == null) {
            return null
        }
        return EvalState.valueOf(value.replace("-", "_").uppercase(
            Locale.getDefault()))
    }

    private val valueMap = mapOf(
        "ACTIVE" to "ACTIVE",
        "INACTIVE" to "INACTIVE"
    )

    @JsonValue
    fun toValue(): String {
         return valueMap[this.name]!!
    }
}

@Reflectable
enum class EvalGranularity {
    LLM_REQUEST_RESPONSE, TURN_BASED, SESSION_BASED;

    @JsonCreator
    fun fromString(value: String?): EvalGranularity? {
        if (value == null) {
            return null
        }
        return EvalGranularity.valueOf(value.replace("-", "_").uppercase(
            Locale.getDefault()))
    }

    private val valueMap = mapOf(
        "LLM_REQUEST_RESPONSE" to "LLM_REQUEST_RESPONSE",
        "TURN_BASED" to "TURN_BASED",
        "SESSION_BASED" to "SESSION_BASED"
    )

    @JsonValue
    fun toValue(): String {
         return valueMap[this.name]!!
    }
}



@Reflectable
enum class IpVerificationStrategy {
    IP, XFF;

    @JsonCreator
    fun fromString(value: String?): IpVerificationStrategy? {
        if (value == null) {
            return null
        }
        return IpVerificationStrategy.valueOf(value.replace("-", "_").uppercase(
            Locale.getDefault()))
    }

    private val valueMap = mapOf(
        "IP" to "ip",
        "XFF" to "xff"
    )

    @JsonValue
    fun toValue(): String {
         return valueMap[this.name]!!
    }
}

