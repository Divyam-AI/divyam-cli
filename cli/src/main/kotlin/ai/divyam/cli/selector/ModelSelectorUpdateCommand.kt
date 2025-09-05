package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseCommand
import ai.divyam.client.ModelSelectorState
import ai.divyam.client.ModelSelectorUpdateRequest
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "update", description = ["Update a selector"])
class ModelSelectorUpdateCommand : BaseCommand() {
    // TODO: Service account id not updatable?. Model info allows update.
    @Option(
        names = ["--id"],
        description = ["The model selector id to update"],
        required = true
    )
    private lateinit var id: String

    @Option(
        names = ["--name"],
        description = ["Optional: New model selector name if change desired"],
    )
    private var name: String? = null

    @Option(
        names = ["--selector-endpoint"],
        description = ["Optional: New model selector endpoint if change " +
                "desired"],
    )
    private var selectorEndpoint: String? = null

    @Option(
        names = ["--state"],
        description = [$$"Optional: New model selector state.  ${COMPLETION-CANDIDATES}"]
    )
    private var state: ModelSelectorState? = null

    override fun execute(): Int {
        val updatedSelector = runBlocking {
            divyamClient.updateModelSelector(
                id = id,
                selectorUpdateRequest = ModelSelectorUpdateRequest(
                    name = name,
                    state = state,
                    endpoint = selectorEndpoint
                ),
            )
        }
        printObjs(updatedSelector)
        return 0
    }
}