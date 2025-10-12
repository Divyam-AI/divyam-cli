package ai.divyam.cli.sa

import ai.divyam.cli.base.BaseCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine

@CommandLine.Command(name = "ls", description = ["List service accounts"])
class SaListCommand : BaseCommand() {
    @CommandLine.Option(
        names = ["-o", "--org-id"],
        description = ["the org id"],
        required = true
    )
    var orgId: Int = 0

    override fun execute(): Int {
        runBlocking {
            val orgs = divyamClient.listServiceAccounts(orgId = orgId)
            printObjs(orgs)
        }
        return 0
    }
}