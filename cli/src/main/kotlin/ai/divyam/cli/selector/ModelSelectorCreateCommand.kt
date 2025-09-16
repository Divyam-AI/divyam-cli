package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseCommand
import ai.divyam.client.data.models.ModelSelectorCreateRequest
import ai.divyam.client.data.models.ModelSelectorState
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "create", description = ["Create a selector"])
class ModelSelectorCreateCommand : BaseCommand() {
    @Option(
        names = ["-o", "--org-id"],
        description = ["Required: Organization id to associate the model " +
                "selector with"],
        required = true
    )
    private var orgId: Int = 0

    @Option(
        names = ["--sa-id", "--service-account-id"],
        description = ["Optional: service account id to associate the model selector with"],
    )
    private var serviceAccountId: String? = null

    @Option(
        names = ["--name"],
        description = ["Required: New model selector name"],
        required = true
    )
    private lateinit var name: String

    @Option(
        names = ["--selector-endpoint"],
        description = ["Optional: New model selector endpoint if change " +
                "desired"],
    )
    private var selectorEndpoint: String? = null

    @Option(
        names = ["--state"],
        description = [$$"Required: Selector state. ${COMPLETION-CANDIDATES}"],
        required = true
    )
    private lateinit var state: ModelSelectorState

    override fun execute(): Int {
        val newModelSelector = runBlocking {
            // TODO: Should remove selector endpoint from the db. Maybe store
            //  a version instead.
            divyamClient.createModelSelector(
                selectorCreateRequest = ModelSelectorCreateRequest(
                    orgId = orgId,
                    serviceAccountId = serviceAccountId,
                    name = name,
                    state = state,
                    endpoint = selectorEndpoint
                )
            )
        }
        printObjs(newModelSelector)
        return 0
    }
}