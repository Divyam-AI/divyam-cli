package ai.divyam.cli.model

import ai.divyam.cli.base.BaseCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "get", description = ["Get specific model info"])
class ModelInfoGetCommand : BaseCommand() {
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
            val modelInfo = divyamClient.getModelInfo(
                orgId = orgId,
                modelInfoId = id
            )
            printObjs(modelInfo)
        }
        return 0
    }
}

