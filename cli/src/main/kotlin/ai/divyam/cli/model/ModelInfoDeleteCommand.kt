package ai.divyam.cli.model

import ai.divyam.cli.base.BaseCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "delete", description = ["Delete model info"])
class ModelInfoDeleteCommand : BaseCommand() {
    // TODO: Consistent IDs types and scope of ids for all objects?
    @Option(
        names = ["--id"],
        description = ["Required: Id of model info to update"],
        required = true
    )
    private var id: Int = 0

    @Option(
        names = ["-o", "--org-id"],
        description = ["Required: Organization id to associate the model " +
                "info with"],
        required = true
    )
    private var orgId: Int = 0

    override fun execute(): Int {
        runBlocking {
            divyamClient.deleteModelInfo(
                orgId = orgId,
                modelInfoId = id
            )

            println("Deleted model org:$orgId model-id:$id")
        }
        return 0
    }
}

