package ai.divyam.cli.sa

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.base.HasSecurityPolicy
import ai.divyam.client.IpVerificationStrategy
import ai.divyam.client.ModelAPIAuthMode
import ai.divyam.client.OptimizationGoal
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(
    name = "create",
    description = ["Create a service account"]
)
class SaCreateCommand : BaseCommand(), HasSecurityPolicy {
    @Option(
        names = ["-o", "--org-id"],
        description = ["the org id"],
        required = true
    )
    var orgId: Int = 0

    @Option(
        names = ["--name"],
        description = ["New service account name"],
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
    var isAdmin: Boolean = false

    @Option(
        names = ["--is-org-admin"],
        arity = "1",
        description = ["Optional: indicates if this service account is an org admin account"],
    )
    var isOrgAdmin: Boolean = false

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
        description = ["Optional: Comma separated list of verifications. " +
                $$"Valid values are ${COMPLETION-CANDIDATES}"],
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
        var trafficAllocationConfigObj: Map<String, Double>? = null
        if (trafficAllocationConfig != null) {
            val mapper = getJsonMapper()
            @Suppress("UNCHECKED_CAST")
            trafficAllocationConfigObj = mapper.readValue(
                trafficAllocationConfig,
                Map::class.java
            ) as Map<String, Double>
        }
        val created = runBlocking {
            divyamClient.createServiceAccount(
                orgId = orgId,
                name = name,
                isOrgAdmin = isOrgAdmin,
                isAdmin = isAdmin,
                authModeModelApi =
                    authMode ?: ModelAPIAuthMode.API_KEYS_SAVED,
                trafficAllocationConfig = trafficAllocationConfigObj,
                optimizationGoal = optimizationGoal
                    ?: OptimizationGoal.HIGH_QUALITY,
                securityPolicy = createSecurityPolicy()
            )
        }
        printObjs(created)
        return 0
    }
}