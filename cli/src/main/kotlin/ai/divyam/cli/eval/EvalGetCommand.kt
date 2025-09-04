package ai.divyam.cli.eval

import ai.divyam.cli.base.SaSpecificCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "get")
class EvalGetCommand : SaSpecificCommand() {
    @Option(
        names = ["--id"],
        description = ["The eval id to get"],
        required = true
    )
    var evalId: Int = 0

    override fun execute(): Int {
        val newEval = runBlocking {
            divyamClient.getEval(
                serviceAccountId = serviceAccountId,
                evalId = evalId
            )
        }
        printObjs(newEval)
        return 0
    }
}