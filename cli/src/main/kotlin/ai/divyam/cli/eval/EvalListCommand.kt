package ai.divyam.cli.eval

import ai.divyam.cli.base.SaSpecificCommand
import ai.divyam.data.model.EvalState
import kotlinx.coroutines.runBlocking
import picocli.CommandLine

@CommandLine.Command(
    name = "ls",
    description = ["List evals for a service account."]
)
class EvalListCommand : SaSpecificCommand() {
    @CommandLine.Option(
        names = ["--states"],
        split = ",",
        description = [$$"Optional: List of selectors with states to show. ${COMPLETION-CANDIDATES}"]
    )
    private var states: List<EvalState>? = null

    override fun execute(): Int {
        runBlocking {
            val evals =
                divyamClient.listEvals(
                    serviceAccountId = serviceAccountId,
                    evalState = states
                )
            printObjs(evals)
        }
        return 0
    }
}