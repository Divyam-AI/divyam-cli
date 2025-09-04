package ai.divyam.cli.org

import ai.divyam.cli.base.BaseCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine

@CommandLine.Command(name = "ls")
class OrgListCommand : BaseCommand() {

    override fun execute(): Int {
        runBlocking {
            val orgs = divyamClient.listOrgs()
            printObjs(orgs)
        }
        return 0
    }
}