package ai.divyam.cli.eval

import ai.divyam.cli.base.SaSpecificCommand
import ai.divyam.client.EvalState
import kotlinx.coroutines.runBlocking
import picocli.CommandLine

@CommandLine.Command(name = "ls")
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
                    states = states
                )
            printObjs(evals)
        }
        return 0
    }
}