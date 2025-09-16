package ai.divyam.client

import ai.divyam.client.data.models.ChatCompletionDebugResponse
import ai.divyam.client.data.models.ChatCompletionResponse
import ai.divyam.client.data.models.ChatRequest
import ai.divyam.client.data.models.Eval
import ai.divyam.client.data.models.EvalCreateRequest
import ai.divyam.client.data.models.EvalState
import ai.divyam.client.data.models.EvalUpdateRequest
import ai.divyam.client.data.models.ModelProviderInfo
import ai.divyam.client.data.models.ModelProviderInfoCreation
import ai.divyam.client.data.models.ModelProviderInfoUpdation
import ai.divyam.client.data.models.ModelSelector
import ai.divyam.client.data.models.ModelSelectorCreateRequest
import ai.divyam.client.data.models.ModelSelectorState
import ai.divyam.client.data.models.ModelSelectorUpdateRequest
import ai.divyam.client.data.models.Org
import ai.divyam.client.data.models.OrgUpdateRequest
import ai.divyam.client.data.models.SecurityPolicy
import ai.divyam.client.data.models.ServiceAccount
import ai.divyam.client.data.models.ServiceAccountCreateRequest
import ai.divyam.client.data.models.ServiceAccountUpdateRequest
import ai.divyam.client.data.models.User
import ai.divyam.client.data.models.UserCreateRequest
import ai.divyam.client.data.models.UserUpdateRequest
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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
import javax.net.ssl.X509TrustManager

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

    @Suppress("unused")
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
        userCreateRequest: UserCreateRequest
    ): User {
        val httpResponse =
            client.post("$endpoint/v1/orgs/${userCreateRequest.orgId}/users/") {
                headers.appendAll(headers())
                contentType(ContentType.Application.Json)
                setBody(userCreateRequest)
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
        serviceAccountCreateRequest: ServiceAccountCreateRequest
    ): ServiceAccount {
        // TODO: no defaults in client/tools
        val trafficAllocationConfig = if (serviceAccountCreateRequest
                .trafficAllocationConfig.isEmpty()
        ) {
            mapOf(
                "control" to 10.0,
                "selector_disabled" to 90.0
            )
        } else {
            serviceAccountCreateRequest.trafficAllocationConfig
        }

        val payload =
            serviceAccountCreateRequest.copy(trafficAllocationConfig = trafficAllocationConfig)

        val httpResponse =
            client.post(
                "$endpoint/v1/orgs/${
                    serviceAccountCreateRequest
                        .orgId
                }/service_accounts/"
            ) {
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