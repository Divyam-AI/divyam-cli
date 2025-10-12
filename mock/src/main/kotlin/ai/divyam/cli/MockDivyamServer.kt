package ai.divyam.cli

import ai.divyam.data.model.ChatCompletionResponse
import ai.divyam.data.model.ChatRequest
import ai.divyam.data.model.Choice
import ai.divyam.data.model.Eval
import ai.divyam.data.model.EvalCreateRequest
import ai.divyam.data.model.EvalUpdateRequest
import ai.divyam.data.model.Message
import ai.divyam.data.model.ModelProviderInfo
import ai.divyam.data.model.ModelProviderInfoCreation
import ai.divyam.data.model.ModelProviderInfoUpdation
import ai.divyam.data.model.ModelSelector
import ai.divyam.data.model.ModelSelectorCreateRequest
import ai.divyam.data.model.ModelSelectorState
import ai.divyam.data.model.ModelSelectorUpdateRequest
import ai.divyam.data.model.OrgInput
import ai.divyam.data.model.OrgUpdateRequest
import ai.divyam.data.model.ServiceAccount
import ai.divyam.data.model.ServiceAccountCreateRequest
import ai.divyam.data.model.ServiceAccountUpdateRequest
import ai.divyam.data.model.User
import ai.divyam.data.model.UserCreateRequest
import ai.divyam.data.model.UserUpdateRequest
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.util.normalizePathComponents
import kotlinx.coroutines.delay
import picocli.CommandLine
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.system.exitProcess

@CommandLine.Command(
    name = "server",
    mixinStandardHelpOptions = true,
    version = ["1.0"],
    description = ["Start the Ktor server"]
)
class ServerCommand : Callable<Int> {

    @CommandLine.Option(
        names = ["-p", "--port"],
        description = ["Port to bind the server to (default: 8080)"],
        defaultValue = "8080"
    )
    private var port: Int = 8080

    @CommandLine.Option(
        names = ["-P", "--password"],
        description = ["Password to authenticate mock user"],
    )
    private var password: String = "divyam123"

    @CommandLine.Option(
        names = ["-h", "--host"],
        description = ["Host to bind the server to (default: 0.0.0.0)"],
        defaultValue = "0.0.0.0"
    )
    private var host: String = "0.0.0.0"

    override fun call(): Int {
        println("starting on port $port")
        try {
            embeddedServer(
                Netty,
                port = port,
                host = host,
            ) {
                install(ContentNegotiation) {
                    jackson {
                        registerKotlinModule()
                        // allow unknown props (models are authoritative)
                        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        // case-insensitive enums
                        @Suppress("DEPRECATION")
                        configure(
                            MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS,
                            true
                        )
                    }
                }
                configureRouting(password)
            }.start(wait = true)
            return 0
        } catch (e: Exception) {
            System.err.println("Failed to start server: ${e.message}")
            return 1
        }
    }
}

fun main(args: Array<String>) {
    val exitCode = CommandLine(ServerCommand()).execute(*args)
    exitProcess(exitCode)
}

data class ModelProvider(val id: Int, val name: String)

/**
 *  Mock data stores (WIP) does not yet simulate a server.
 */
object MockDataStore {
    val orgs = mutableMapOf<Int, OrgInput>()
    val users = mutableMapOf<String, User>() // keyed by email_id
    val serviceAccounts =
        mutableMapOf<String, ServiceAccount>() // keyed by serviceAccount.id
    val modelInfos = mutableMapOf<Int, ModelProviderInfo>() // keyed by id
    val modelSelectors =
        mutableMapOf<Int, ModelSelector>() // keyed by id (int as models expect)
    val evals =
        mutableMapOf<String, MutableMap<Int, Eval>>() // keyed by serviceAccountId -> (evalId -> Eval)

    val providers =
        mutableMapOf<String, ModelProvider>() // provider name -> provider struct

    val orgIdCounter = AtomicInteger(1)
    val modelInfoIdCounter = AtomicInteger(1)
    val evalIdCounter = AtomicInteger(1)
    val modelProviderCounter = AtomicInteger(1)
    val modelSelectorIdCounter = AtomicInteger(1)

    init {
        // seed sample org
        val sampleOrg =
            OrgInput(id = 1, name = "Sample Org", securityPolicy = null)
        orgs[1] = sampleOrg
    }
}

fun Application.configureRouting(password: String) {
    routing {
        // Normalize URLs
        intercept(ApplicationCallPipeline.Call) {
            val uri = context.request.uri
            if (uri.endsWith("/") && uri != "/") {
                context.respondRedirect {
                    pathSegments = pathSegments.normalizePathComponents()
                }
                finish() // stop further processing
            }
        }

        // Health and status endpoints (from spec)
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        get("/status") {
            call.respond(mapOf("status" to "ok"))
        }

        get("/v1/admin/status") {
            call.respond(mapOf("status" to "ok", "admin" to true))
        }

        // Authentication endpoint
        post("/token") {
            val credentials = call.receive<Map<String, String>>()
            val email_id = credentials["email_id"]
            val incomingPassword = credentials["password"]

            if (email_id == "admin@dashboard.divyam.ai" && incomingPassword == password
            ) {
                call.respond(
                    mapOf(
                        "bearer_token" to
                                "mock-bearer-token-12345", "name" to email_id
                    )
                )
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
            }
        }

        route("/v1") {
            // -----------------------
            // Orgs routes
            // -----------------------
            route("/orgs") {
                get {
                    call.respond(MockDataStore.orgs.values.toList())
                }

                post {
                    val org = call.receive<OrgInput>()
                    val id = MockDataStore.orgIdCounter.getAndIncrement()
                    val newOrg = org.copy(id = id)
                    MockDataStore.orgs[id] = newOrg
                    call.respond(HttpStatusCode.Created, newOrg)
                }

                get("/{org_id}") {
                    val org_id = call.parameters["org_id"]?.toIntOrNull()
                    val org = org_id?.let { MockDataStore.orgs[it] }
                    if (org != null) call.respond(org)
                    else call.respond(HttpStatusCode.NotFound, "Org not found")
                }

                patch("/{org_id}") {
                    val org_id = call.parameters["org_id"]?.toIntOrNull()
                    val updateRequest = call.receive<OrgUpdateRequest>()
                    val existingOrg = org_id?.let { MockDataStore.orgs[it] }
                    if (existingOrg != null) {
                        val updatedOrg = existingOrg.copy(
                            name = updateRequest.name ?: existingOrg.name,
                            securityPolicy = updateRequest.securityPolicy
                                ?: existingOrg.securityPolicy
                        )
                        MockDataStore.orgs[org_id] = updatedOrg
                        call.respond(updatedOrg)
                    } else call.respond(
                        HttpStatusCode.NotFound,
                        "Org not found"
                    )
                }

                // -----------------------
                // Users under org
                // -----------------------
                get("/{org_id}/users") {
                    val org_id = call.parameters["org_id"]?.toIntOrNull()
                    val users =
                        MockDataStore.users.values.filter { it.orgId == org_id }
                    call.respond(users)
                }

                post("/{org_id}/users") {
                    val userRequest = call.receive<UserCreateRequest>()
                    val org = MockDataStore.orgs[userRequest.orgId]
                    if (org == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@post
                    }
                    // build User to match the data model exactly
                    val user = User(
                        emailId = userRequest.emailId,
                        name = userRequest.name,
                        isOrgAdmin = userRequest.isOrgAdmin,
                        isAdmin = userRequest.isAdmin,
                        orgId = userRequest.orgId,
                        orgName = org.name,
                        securityPolicy = userRequest.securityPolicy
                    )
                    MockDataStore.users[user.emailId] = user
                    call.respond(HttpStatusCode.Created, user)
                }

                // -----------------------
                // Service accounts under org
                // -----------------------
                get("/{org_id}/service_accounts") {
                    val org_id = call.parameters["org_id"]?.toIntOrNull()
                    val accounts =
                        MockDataStore.serviceAccounts.values.filter { it.orgId == org_id }
                    call.respond(accounts)
                }

                post("/{org_id}/service_accounts") {
                    val request = call.receive<ServiceAccountCreateRequest>()
                    val org = MockDataStore.orgs[request.orgId]
                    if (org == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@post
                    }

                    // create ServiceAccount matching the data model
                    val sa = ServiceAccount(
                        id = "sa-${System.currentTimeMillis()}",
                        name = request.name,
                        orgId = request.orgId,
                        orgName = org.name,
                        apiKey = null,
                        divyamAuthKeyHashed = sha256Hex(randomString(32)),
                        optimizationGoal = request.optimizationGoal,
                        authmodeModelApi = request.authmodeModelApi,
                        trafficAllocationConfig = request.trafficAllocationConfig,
                        isOrgAdmin = request.isOrgAdmin,
                        isAdmin = request.isAdmin,
                        securityPolicy = request.securityPolicy
                    )

                    MockDataStore.serviceAccounts[sa.id] = sa
                    call.respond(HttpStatusCode.OK, sa)
                }

                // -----------------------
                // ModelProviderInfo endpoints under org
                // -----------------------
                get("/{org_id}/models_info/") {
                    val org_id = call.parameters["org_id"]?.toIntOrNull()
                    val org = org_id?.let { MockDataStore.orgs[it] }
                    if (org == null) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    val service_account_id =
                        call.request.queryParameters["service_account_id"]
                    val modelInfos = MockDataStore.modelInfos.values.filter {
                        it.orgName == org.name && (service_account_id == null || it.serviceAccountId == service_account_id)
                    }
                    call.respond(modelInfos)
                }

                post("/{org_id}/models_info/") {
                    val org_id = call.parameters["org_id"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val org =
                        MockDataStore.orgs[org_id] ?: return@post call.respond(
                            HttpStatusCode.NotFound
                        )
                    val request = call.receive<ModelProviderInfoCreation>()

                    val id = MockDataStore.modelInfoIdCounter.getAndIncrement()

                    val provider =
                        MockDataStore.providers.computeIfAbsent(request.nameProvider) {
                            ModelProvider(
                                id = MockDataStore.modelProviderCounter.getAndIncrement(),
                                name = it
                            )
                        }

                    val modelInfo = ModelProviderInfo(
                        id = id,
                        orgId = org_id,
                        nameProvider = request.nameProvider,
                        idProvider = provider.id,
                        nameModel = request.nameModel,
                        idModel = id,
                        endpoint = request.endpoint,
                        maskedApiKeyModel = sha256Hex(
                            request.apiKeyModel ?: ""
                        ),
                        serviceAccountId = request.serviceAccountId
                            ?: "unknown",
                        orgName = org.name,
                        configsModel = request.configsModel,
                        supportedModalities = request.supportedModalities,
                        textPricing = request.textPricing,
                        currency = request.currency,
                        perNTokens = request.perNTokens,
                        isActive = true,
                        isSelectionEnabled = true
                    )

                    MockDataStore.modelInfos[id] = modelInfo
                    call.respond(HttpStatusCode.Created, modelInfo)
                }

                get("/{org_id}/models_info/{model_info_id}/") {
                    val model_info_id =
                        call.parameters["model_info_id"]?.toIntOrNull()
                    val modelInfo =
                        model_info_id?.let { MockDataStore.modelInfos[it] }
                    if (modelInfo != null) call.respond(modelInfo)
                    else call.respond(
                        HttpStatusCode.NotFound,
                        "Model info not found"
                    )
                }

                post("/{org_id}/models_info/{model_info_id}/") {
                    val model_info_id =
                        call.parameters["model_info_id"]?.toIntOrNull()
                    val existingInfo =
                        model_info_id?.let { MockDataStore.modelInfos[it] }
                    if (existingInfo == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            "Model info not found"
                        )
                        return@post
                    }

                    val updateRequest =
                        call.receive<ModelProviderInfoUpdation>()

                    val provider = MockDataStore.providers.computeIfAbsent(
                        updateRequest.providerName ?: existingInfo.nameProvider
                    ) {
                        ModelProvider(
                            id = MockDataStore.modelProviderCounter.getAndIncrement(),
                            name = it
                        )
                    }

                    val updatedInfo = existingInfo.copy(
                        nameProvider = provider.name,
                        idProvider = provider.id,
                        serviceAccountId = updateRequest.serviceAccountId
                            ?: existingInfo.serviceAccountId,
                        nameModel = updateRequest.modelName
                            ?: existingInfo.nameModel,
                        endpoint = updateRequest.endpoint
                            ?: existingInfo.endpoint,
                        maskedApiKeyModel = updateRequest.apiKeyModel?.let {
                            sha256Hex(
                                it
                            )
                        } ?: existingInfo.maskedApiKeyModel,
                        supportedModalities = updateRequest.supportedModalities
                            ?: existingInfo.supportedModalities,
                        configsModel = updateRequest.configsModel
                            ?: existingInfo.configsModel,
                        textPricing = updateRequest.textPricing
                            ?: existingInfo.textPricing,
                        currency = updateRequest.currency
                            ?: existingInfo.currency,
                        perNTokens = updateRequest.perNTokens
                            ?: existingInfo.perNTokens
                    )

                    MockDataStore.modelInfos[model_info_id] = updatedInfo
                    call.respond(updatedInfo)
                }
            }

            // -----------------------
            // Users (top-level)
            // -----------------------
            route("/users") {
                get("/") {
                    call.respond(MockDataStore.users.values.toList())
                }

                get("/{email_id}/") {
                    val email_id = call.parameters["email_id"]
                    val user = email_id?.let { MockDataStore.users[it] }
                    if (user != null) call.respond(user)
                    else call.respond(HttpStatusCode.NotFound, "User not found")
                }

                post("/{email_id}/") {
                    val email_id =
                        call.parameters["email_id"] ?: return@post call.respond(
                            HttpStatusCode.BadRequest
                        )
                    val updateRequest = call.receive<UserUpdateRequest>()
                    val existingUser = MockDataStore.users[email_id]
                    if (existingUser != null) {
                        val updatedUser = existingUser.copy(
                            name = updateRequest.name ?: existingUser.name,
                            isOrgAdmin = updateRequest.isOrgAdmin
                                ?: existingUser.isOrgAdmin,
                            isAdmin = updateRequest.isAdmin
                                ?: existingUser.isAdmin,
                            securityPolicy = updateRequest.securityPolicy
                                ?: existingUser.securityPolicy
                        )
                        MockDataStore.users[email_id] = updatedUser
                        call.respond(updatedUser)
                    } else call.respond(
                        HttpStatusCode.NotFound,
                        "User not found"
                    )
                }
            }

            // -----------------------
            // Service accounts (top-level)
            // -----------------------
            route("/service_accounts") {
                get("/{service_account_id}/") {
                    val service_account_id =
                        call.parameters["service_account_id"]
                    val sa =
                        service_account_id?.let { MockDataStore.serviceAccounts[it] }
                    if (sa != null) call.respond(sa)
                    else call.respond(
                        HttpStatusCode.NotFound,
                        "Service account not found"
                    )
                }

                post("/{service_account_id}/") {
                    val service_account_id =
                        call.parameters["service_account_id"]
                            ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val updateRequest =
                        call.receive<ServiceAccountUpdateRequest>()
                    val existingAccount =
                        MockDataStore.serviceAccounts[service_account_id]
                    if (existingAccount != null) {
                        val updatedAccount = existingAccount.copy(
                            name = updateRequest.name ?: existingAccount.name,
                            optimizationGoal = updateRequest.optimizationGoal
                                ?: existingAccount.optimizationGoal,
                            authmodeModelApi = updateRequest.authmodeModelApi
                                ?: existingAccount.authmodeModelApi,
                            trafficAllocationConfig = updateRequest.trafficAllocationConfig
                                ?: existingAccount.trafficAllocationConfig,
                            isOrgAdmin = updateRequest.isOrgAdmin
                                ?: existingAccount.isOrgAdmin,
                            isAdmin = updateRequest.isAdmin
                                ?: existingAccount.isAdmin,
                            securityPolicy = updateRequest.securityPolicy
                                ?: existingAccount.securityPolicy
                        )
                        MockDataStore.serviceAccounts[service_account_id] =
                            updatedAccount
                        call.respond(updatedAccount)
                    } else call.respond(
                        HttpStatusCode.NotFound,
                        "Service account not found"
                    )
                }

                // -----------------------
                // Evals under a service account
                // -----------------------
                get("/{service_account_id}/evals/") {
                    val service_account_id =
                        call.parameters["service_account_id"]
                            ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val states = call.request.queryParameters.getAll("states")
                    val evals =
                        MockDataStore.evals[service_account_id]?.values?.toList()
                            ?: emptyList()
                    val filtered = if (!states.isNullOrEmpty()) {
                        evals.filter { eval -> states.contains(eval.state.toString()) }
                    } else evals
                    if (filtered.isEmpty()) call.respond(HttpStatusCode.NotFound)
                    else call.respond(filtered)
                }

                post("/{service_account_id}/evals/") {
                    val service_account_id =
                        call.parameters["service_account_id"]
                            ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val createRequest = call.receive<EvalCreateRequest>()

                    val evalId = MockDataStore.evalIdCounter.getAndIncrement()
                    // create Eval as per the data model
                    val eval = Eval(
                        id = evalId,
                        orgId = createRequest.orgId,
                        serviceAccountId = createRequest.serviceAccountId,
                        name = createRequest.name,
                        granularity = createRequest.granularity,
                        className = createRequest.className,
                        classInitConfig = createRequest.classInitConfig,
                        samplingConfig = createRequest.samplingConfig,
                        state = createRequest.state,
                        createdAt = (System.currentTimeMillis() / 1000).toInt()
                    )

                    MockDataStore.evals.computeIfAbsent(service_account_id) { mutableMapOf() }[evalId] =
                        eval
                    call.respond(HttpStatusCode.Created, eval)
                }

                get("/{service_account_id}/evals/{eval_id}/") {
                    val service_account_id =
                        call.parameters["service_account_id"]
                            ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val eval_id = call.parameters["eval_id"]?.toIntOrNull()
                    val eval = eval_id?.let {
                        MockDataStore.evals[service_account_id]?.get(it)
                    }
                    if (eval != null) call.respond(eval) else call.respond(
                        HttpStatusCode.NotFound,
                        "Eval not found"
                    )
                }

                post("/{service_account_id}/evals/{eval_id}/") {
                    val service_account_id =
                        call.parameters["service_account_id"]
                            ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val eval_id = call.parameters["eval_id"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val existingEval =
                        MockDataStore.evals[service_account_id]?.get(eval_id)
                    if (existingEval != null) {
                        val updateRequest = call.receive<EvalUpdateRequest>()
                        val updatedEval = existingEval.copy(
                            name = updateRequest.name ?: existingEval.name,
                            granularity = updateRequest.granularity
                                ?: existingEval.granularity,
                            className = updateRequest.className
                                ?: existingEval.className,
                            classInitConfig = updateRequest.classInitConfig
                                ?: existingEval.classInitConfig,
                            samplingConfig = updateRequest.samplingConfig
                                ?: existingEval.samplingConfig,
                            state = updateRequest.state ?: existingEval.state
                            // createdAt remains unchanged (model expects createdAt)
                        )
                        MockDataStore.evals[service_account_id]!![eval_id] =
                            updatedEval
                        call.respond(updatedEval)
                    } else call.respond(
                        HttpStatusCode.NotFound,
                        "Eval not found"
                    )
                }
            }

            // -----------------------
            // Model selectors (top-level)
            // -----------------------
            route("/model_selectors") {
                get("/") {
                    val orgId =
                        call.request.queryParameters["org_id"]?.toIntOrNull()
                    val service_account_id =
                        call.request.queryParameters["service_account_id"]
                    val states = call.request.queryParameters.getAll("states")
                    var selectors = MockDataStore.modelSelectors.values.toList()
                    orgId?.let {
                        selectors = selectors.filter { it.orgId == orgId }
                    }
                    service_account_id?.let {
                        selectors =
                            selectors.filter { it.serviceAccountId == service_account_id }
                    }
                    if (!states.isNullOrEmpty()) {
                        selectors = selectors.filter { selector ->
                            states.contains(selector.state.toString())
                        }
                    }
                    if (selectors.isEmpty()) call.respond(HttpStatusCode.NotFound)
                    else call.respond(selectors)
                }

                post("/") {
                    val createRequest =
                        call.receive<ModelSelectorCreateRequest>()
                    val id =
                        MockDataStore.modelSelectorIdCounter.getAndIncrement()
                    val createdAtInt =
                        (System.currentTimeMillis() / 1000).toInt()
                    val selector = ModelSelector(
                        id = id,
                        name = createRequest.name,
                        orgId = createRequest.orgId,
                        serviceAccountId = createRequest.serviceAccountId,
                        state = createRequest.state
                            ?: ModelSelectorState.INACTIVE,
                        endpoint = createRequest.endpoint,
                        createdAt = createdAtInt
                    )
                    MockDataStore.modelSelectors[id] = selector
                    call.respond(HttpStatusCode.Created, selector)
                }

                get("/{model_selector_id}/") {
                    val id = call.parameters["model_selector_id"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest)
                    val selector = MockDataStore.modelSelectors[id]
                    if (selector != null) call.respond(selector)
                    else call.respond(
                        HttpStatusCode.NotFound,
                        "Model selector not found"
                    )
                }

                post("/{model_selector_id}/") {
                    val id = call.parameters["model_selector_id"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val existingSelector = MockDataStore.modelSelectors[id]
                    if (existingSelector != null) {
                        val updateRequest =
                            call.receive<ModelSelectorUpdateRequest>()
                        val updatedSelector = existingSelector.copy(
                            name = updateRequest.name ?: existingSelector.name,
                            state = updateRequest.state
                                ?: existingSelector.state,
                            endpoint = updateRequest.endpoint
                                ?: existingSelector.endpoint
                            // createdAt remains unchanged (model uses createdAt)
                        )
                        MockDataStore.modelSelectors[id] = updatedSelector
                        call.respond(updatedSelector)
                    } else call.respond(
                        HttpStatusCode.NotFound,
                        "Model selector not found"
                    )
                }
            }

            // -----------------------
            // Chat completions
            // -----------------------
            post("/chat/completions") {
                val chatRequest = call.receive<ChatRequest>()
                call.request.queryParameters["mock_selector"]?.toBoolean()
                    ?: false
                call.request.queryParameters["mock_model"]?.toBoolean()
                    ?: false

                delay(100) // simulate work

                val randomText = TextGenerator().generateText(
                    Random.nextInt(6),
                    Random.nextInt(10)
                )

                // Always return a valid ChatCompletionResponse using the data models
                val choiceMessage = Message(

                    content = if (chatRequest.stream == true) {
                        TODO("unimplemented")
                    } else {
                        randomText
                    },
                    refusal = null,
                    role = "assistant",
                    annotations = null,
                    audio = null,
                    functionCall = null,
                    toolCalls = null
                )

                val choice = Choice(
                    finishReason = "stop",
                    index = 0,
                    logprobs = null,
                    message = choiceMessage
                )

                val response = ChatCompletionResponse(
                    id = "chatcmpl-${System.currentTimeMillis()}",
                    choices = listOf(choice),
                    created = System.currentTimeMillis() / 1000,
                    model = chatRequest.model,
                    objectType = "chat.completion",
                    serviceTier = null,
                    systemFingerprint = null,
                    usage = null
                )

                call.respond(response)
            }
        }
    }
}

fun randomString(length: Int): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length).map { chars.random() }.joinToString("")
}

fun sha256Hex(input: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(input)
    return digest.joinToString("") { "%02x".format(it) }
}

fun sha256Hex(input: String): String =
    sha256Hex(input.toByteArray(Charsets.UTF_8))

