/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.org

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.base.HasSecurityPolicy
import ai.divyam.data.model.IpVerificationStrategy
import ai.divyam.data.model.OrgInput
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "create", description = ["Create org"])
class OrgCreateCommand : BaseCommand(), HasSecurityPolicy {
    @Option(
        names = ["--name"],
        description = ["New org name"],
        required = true
    )
    private lateinit var name: String

    @Option(
        names = ["--allowed-ip-networks"],
        description = ["Optional: Comma separated list of ip networks that should be allowed to use this org"],
        split = ","
    )
    override var allowedIpNetworks: List<String>? = null

    @Option(
        names = ["--blocked-ip-networks"],
        description = ["Optional: Comma separated list of ip networks that should be blocked from using this org"],
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
        val newOrg = runBlocking {
            divyamClient.createOrg(
                orgInput = OrgInput(
                    name = name,
                    securityPolicy = createSecurityPolicy()
                )
            )
        }
        printObjs(newOrg)
        return 0
    }
}