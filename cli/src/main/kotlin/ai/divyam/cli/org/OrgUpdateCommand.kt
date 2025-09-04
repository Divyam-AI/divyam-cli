package ai.divyam.cli.org

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.base.HasSecurityPolicy
import ai.divyam.client.IpVerificationStrategy
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "update")
class OrgUpdateCommand : BaseCommand(), HasSecurityPolicy {
    @Option(
        names = ["--id"],
        description = ["The org id to update"],
        required = true
    )
    var orgId: Int = 0

    @Option(
        names = ["--name"],
        description = ["Optional: Modified org name if change desired"],
    )
    private var name: String? = null

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
        val newOrg = runBlocking {
            var org = divyamClient.getOrgById(orgId)
            if (name != null) {
                org = org.copy(name = name!!)
            }

            org =
                org.copy(securityPolicy = updateSecurityPolicyFromArgs(org.securityPolicy))
            divyamClient.updateOrg(
                orgId = orgId,
                name = org.name,
                securityPolicy = org.securityPolicy
            )
        }
        printObjs(newOrg)
        return 0
    }
}