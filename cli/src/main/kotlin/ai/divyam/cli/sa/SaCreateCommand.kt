/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.sa

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.base.HasSecurityPolicy
import ai.divyam.data.model.IpVerificationStrategy
import ai.divyam.data.model.ModelAPIAuthMode
import ai.divyam.data.model.OptimizationGoal
import ai.divyam.data.model.RetryFallbackPolicy
import ai.divyam.data.model.ServiceAccountCreateRequest
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File

@CommandLine.Command(
    name = "create",
    description = ["Create a service account"]
)
class SaCreateCommand : BaseCommand(), HasSecurityPolicy {
    @Option(
        names = ["-o", "--org-id"],
        description = ["Required: Organization id to associate the service account with. If omitted, falls back to the DIVYAM_ORG_ID environment variable, then the current config file."],
    )
    var orgId: Int? = null

    @Option(
        names = ["--name"],
        description = ["New service account name"],
        required = true
    )
    lateinit var name: String

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

    @Option(
        names = ["--retry-fallback-policy-file"],
        description = ["Optional: Path to a JSON file with retry/fallback policy (e.g. retry_delay_s, max_retries, max_fallback_hops)"],
    )
    var retryFallbackPolicyFile: File? = null

    @Option(
        names = ["--retry-fallback-policy"],
        description = ["Optional: Inline JSON for retry/fallback policy. Example: {\"retry_delay_s\":3,\"max_fallback_hops\":4}"],
    )
    var retryFallbackPolicyInline: String? = null

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
        val retryFallbackPolicy = parseRetryFallbackPolicy()
        val created = runBlocking {
            val resolvedOrgId = getOrgId(orgId)
            divyamClient.createServiceAccount(
                orgId = resolvedOrgId,
                serviceAccountCreateRequest = ServiceAccountCreateRequest(
                    orgId = resolvedOrgId,
                    name = name,
                    isOrgAdmin = isOrgAdmin,
                    isAdmin = isAdmin,
                    authmodeModelApi =
                        authMode ?: ModelAPIAuthMode.API_KEYS_SAVED,
                    trafficAllocationConfig = trafficAllocationConfigObj
                        ?: emptyMap(),
                    optimizationGoal = optimizationGoal
                        ?: OptimizationGoal.HIGH_QUALITY,
                    securityPolicy = createSecurityPolicy(),
                    retryFallbackPolicy = retryFallbackPolicy
                )
            )
        }
        printObjs(created)
        return 0
    }

    private fun parseRetryFallbackPolicy(): RetryFallbackPolicy? {
        retryFallbackPolicyFile?.let { file ->
            if (file.exists()) {
                return getJsonMapper().readValue(file, RetryFallbackPolicy::class.java)
            }
            throw IllegalArgumentException("Retry fallback policy file not found: ${file.absolutePath}")
        }
        retryFallbackPolicyInline?.let { json ->
            return getJsonMapper().readValue(json, RetryFallbackPolicy::class.java)
        }
        return null
    }
}