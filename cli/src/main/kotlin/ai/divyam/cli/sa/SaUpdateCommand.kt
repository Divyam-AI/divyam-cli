package ai.divyam.cli.sa

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.base.HasSecurityPolicy
import ai.divyam.data.model.IpVerificationStrategy
import ai.divyam.data.model.ModelAPIAuthMode
import ai.divyam.data.model.OptimizationGoal
import ai.divyam.data.model.ServiceAccount
import ai.divyam.data.model.ServiceAccountUpdateRequest
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option
import java.math.BigDecimal

@CommandLine.Command(
    name = "update",
    description = ["Update a service account"]
)
class SaUpdateCommand : BaseCommand(), HasSecurityPolicy {
    @Option(
        names = ["--id"],
        description = ["Required: service accounts id for the service account to update"],
        required = true
    )
    private lateinit var id: String

    @Option(
        names = ["--name"],
        description = ["Optional: new service account name if change desired"],
    )
    var name: String? = null

    @Option(
        names = ["--traffic-allocation-config"],
        description = ["Optional: Selector traffic_allocation_config: Default: {\"control\": 10.0, \"selector_disabled\": 90.0}"],
    )
    var trafficAllocationConfig: String? = null

    @Option(
        names = ["--optimization-goal"],
        description = [$$"Optional: Goal for Selector to Optimize - ${COMPLETION-CANDIDATES}"],
    )
    var optimizationGoal: OptimizationGoal? = null

    @Option(
        names = ["--auth-mode"],
        description = [$$"Optional: Authorization mechanism - ${COMPLETION-CANDIDATES}"],
    )
    var authMode: ModelAPIAuthMode? = null

    @Option(
        names = ["--is-admin"],
        arity = "1",
        description = ["Optional: indicates if this service account should be considered as an admin account"],
    )
    var isAdmin: Boolean? = null

    @Option(
        names = ["--is-org-admin"],
        arity = "1",
        description = ["Optional: indicates if this service account is an org admin account"],
    )
    var isOrgAdmin: Boolean? = null

    @Option(
        names = ["--regenerate-api-key"],
        description = ["Optional: indicates if API key for this service account should be regenerated"],
        defaultValue = "false"
    )
    var regenerateApiKey: Boolean = false

    @Option(
        names = ["--allowed-ip-networks"],
        description = ["Optional: Comma separated list of ip networks that should be allowed to use this service account"],
        split = ","
    )
    override var allowedIpNetworks: List<String>? = null

    @Option(
        names = ["--blocked-ip-networks"],
        description = ["Optional: Comma separated list of ip networks that should be blocked from using this service account"],
        split = ","
    )
    override var blockedIpNetworks: List<String>? = null

    @Option(
        names = ["--ip-verifications"],
        description = [$$"Optional: Comma separated list of verifications. Valid values are ${COMPLETION-CANDIDATES}"],
        split = ","
    )
    override var ipVerifications: List<IpVerificationStrategy>? = null

    @Option(
        names = ["--xff-indices"],
        description = ["Optional: Comma separated list of xff indices to extract client-ip from for verification"],
        split = ","
    )
    override var xffIndices: List<Int>? = null

    override fun execute(): Int {
        // Get and update the service account object.
        val sa = getAndUpdateLocalServiceAccount()

        // Apply the updates.
        val updated = runBlocking {
            divyamClient.updateServiceAccount(
                serviceAccountId = sa.id,
                serviceAccountUpdateRequest = ServiceAccountUpdateRequest(
                    name = sa.name,
                    trafficAllocationConfig = sa.trafficAllocationConfig,
                    optimizationGoal = sa.optimizationGoal,
                    authmodeModelApi = sa.authmodeModelApi,
                    isOrgAdmin = sa.isOrgAdmin,
                    isAdmin = sa.isAdmin,
                    regenerateApiKey = regenerateApiKey,
                    securityPolicy = sa.securityPolicy
                )
            )
        }
        printObjs(updated)
        return 0
    }

    /**
     * Fetches a service account, applies updates from command-line arguments, and returns the updated object.
     * * @param args The command-line arguments containing the update values.
     * @return The locally updated ServiceAccount object.
     */
    fun getAndUpdateLocalServiceAccount(): ServiceAccount {
        var sa = runBlocking {
            divyamClient.getServiceAccount(serviceAccountId = id)
        }

        name?.let { sa = sa.copy(name = it.trim()) }
        trafficAllocationConfig?.let {
            val mapper = getJsonMapper()
            @Suppress("UNCHECKED_CAST")
            sa = sa.copy(
                trafficAllocationConfig = mapper.readValue(
                    it,
                    Map::class.java
                ) as Map<String, BigDecimal>
            )
        }

        optimizationGoal?.let { sa = sa.copy(optimizationGoal = it) }

        authMode?.let { sa = sa.copy(authmodeModelApi = it) }

        isAdmin?.let { sa = sa.copy(isAdmin = it) }
        isOrgAdmin?.let { sa = sa.copy(isOrgAdmin = it) }

        sa = updateObjectSecurityPolicyFromArgs(obj = sa)
        return sa
    }

    /**
     * Updates the security policy of an object based on command-line arguments.
     * @param obj The object whose security policy is to be updated.
     */
    fun updateObjectSecurityPolicyFromArgs(obj: ServiceAccount): ServiceAccount {
        val updatedServiceAccount =
            obj.copy(securityPolicy = updateSecurityPolicyFromArgs(obj.securityPolicy))

        return updatedServiceAccount
    }
}