package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseCommand
import ai.divyam.client.ModelSelectorState
import kotlinx.coroutines.runBlocking
import picocli.CommandLine

@CommandLine.Command(name = "ls", description = ["List selectors"])
class ModelSelectorListCommand : BaseCommand() {
    @CommandLine.Option(
        names = ["-o", "--org-id"],
        description = ["Required: organization id to get model selectors for"],
        required = true
    )
    protected var orgId: Int = 0

    @CommandLine.Option(
        names = ["--states"],
        split = ",",
        description = [$$"Optional: List of selectors with states to show. ${COMPLETION-CANDIDATES}"]
    )
    private var states: List<ModelSelectorState>? = null

    override fun execute(): Int {
        runBlocking {
            val selectors =
                divyamClient.getModelSelectors(orgId = orgId, states = states)
            printObjs(selectors)
        }
        return 0
    }
}