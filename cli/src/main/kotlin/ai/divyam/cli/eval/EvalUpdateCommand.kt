package ai.divyam.cli.eval

import ai.divyam.cli.base.SaSpecificCommand
import ai.divyam.data.model.EvalGranularity
import ai.divyam.data.model.EvalState
import ai.divyam.data.model.EvalUpdateRequest

import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "update", description = ["Update an eval."])
class EvalUpdateCommand : SaSpecificCommand() {
    @Option(
        names = ["--id"],
        description = ["The eval id to update"],
        required = true
    )
    var evalId: Int = 0

    @Option(
        names = ["--name"],
        description = ["Optional: New eval name if change desired"],
    )
    private var name: String? = null

    @Option(
        names = ["--granularity"],
        description = [$$"Optional: New granularity of the eval. ${COMPLETION" +
                " - CANDIDATES}"],
    )
    private var granularity: EvalGranularity? = null

    @Option(
        names = ["--class-name"],
        description = ["Optional: New class name of the eval"],
    )
    private var className: String? = null

    @Option(
        names = ["--class-init-config"],
        description = ["Optional: New class init config of the eval as a json" +
                " " +
                "object"],
    )
    private var classInitConfig: String? = null

    @Option(
        names = ["--state"],
        description = [$$"Optional: New eval state.  ${COMPLETION-CANDIDATES}"]
    )
    private var state: EvalState? = null

    override fun execute(): Int {
        val classInitConfigMap = if (classInitConfig != null) {
            @Suppress("UNCHECKED_CAST")
            getJsonMapper()
                .readValue(classInitConfig, Map::class.java) as
                    Map<String, Any>
        } else {
            emptyMap()
        }

        val newEval = runBlocking {
            divyamClient.updateEval(
                serviceAccountId = serviceAccountId,
                evalId = evalId,
                evalUpdateRequest = EvalUpdateRequest(
                    name = name,
                    granularity = granularity,
                    className = className,
                    classInitConfig = classInitConfigMap,
                    state = state
                ),
            )
        }
        printObjs(newEval)
        return 0
    }
}