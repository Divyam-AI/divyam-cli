package ai.divyam.cli.sa

import ai.divyam.cli.base.BaseCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine

@CommandLine.Command(name = "get")
class SaGetCommand : BaseCommand() {
    @CommandLine.Option(
        names = ["--id"],
        description = ["the service account id"],
        required = true
    )
    private lateinit var saId: String

    override fun execute(): Int {
        runBlocking {
            val sa = divyamClient.getServiceAccount(serviceAccountId = saId)
            printObjs(sa)
        }
        return 0
    }
}