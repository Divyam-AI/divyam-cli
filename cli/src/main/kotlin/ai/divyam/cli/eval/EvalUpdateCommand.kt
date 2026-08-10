/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.eval

import ai.divyam.cli.base.SaSpecificCommand
import ai.divyam.data.model.EvalGranularity
import ai.divyam.data.model.EvalState
import ai.divyam.data.model.EvalUpdateRequest
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File

@CommandLine.Command(
    name = "update",
    description = [
        "Update an eval, merging any --evalm8-* flags onto what is already stored",
    ],
)
class EvalUpdateCommand : SaSpecificCommand() {
    @Option(
        names = ["--id"],
        description = ["The eval id to update"],
        required = true
    )
    var evalId: Int = 0

    @Option(
        names = ["-o", "--org-id"],
        description = ["Organization id to associate the eval with. If omitted, falls back to the DIVYAM_ORG_ID environment variable, then the current config file."],
    )
    var orgId: Int? = null

    @Option(
        names = ["--name"],
        description = ["Optional: New eval name if change desired"],
    )
    private var name: String? = null

    @Option(
        names = ["--evalm8-org"],
        description = ["The Evalm8 organisation holding the eval."],
    )
    private var evalm8Org: String? = null

    @Option(
        names = ["--evalm8-project"],
        description = ["The Evalm8 project holding the eval."],
    )
    private var evalm8Project: String? = null

    @Option(
        names = ["--evalm8-eval-name"],
        description = ["The eval as named in Evalm8."],
    )
    private var evalm8EvalName: String? = null

    @Option(
        names = ["--evalm8-eval-ref"],
        description = ["Which version of the Evalm8 eval to pin."],
    )
    private var evalm8EvalRef: String? = null

    @Option(
        names = ["--evalm8-base-url"],
        description = ["Evalm8 endpoint."],
    )
    private var evalm8BaseUrl: String? = null

    @Option(
        names = ["--evalm8-api-key"],
        description = ["Evalm8 api key. This is distinct from the router api key."],
    )
    private var evalm8ApiKey: String? = null

    @Option(
        names = ["--skip-verify"],
        description = ["Update without first checking the eval exists in Evalm8."],
    )
    private var skipVerify: Boolean = false

    @Option(
        names = ["--eval-config"],
        description = [
            "Whole eval as inline JSON. Any flag above overrides it. Mutually exclusive with " +
                "--eval-config-file.",
        ],
    )
    private var evalConfig: String? = null

    @Option(
        names = ["--eval-config-file"],
        description = [
            "Whole eval as a JSON file. Any flag above overrides it. Mutually exclusive with " +
                "--eval-config.",
        ],
    )
    private var evalConfigFile: File? = null

    @Option(
        names = ["--granularity"],
        description = [$$"Optional: New granularity of the eval. ${COMPLETION-CANDIDATES}"],
    )
    private var granularity: EvalGranularity? = null

    @Option(
        names = ["--state"],
        description = [$$"Optional: New eval state.  ${COMPLETION-CANDIDATES}"]
    )
    private var state: EvalState? = null

    @Option(
        names = ["--is-primary"],
        description = ["Whether the eval is primary"],
        arity = "1"
    )
    private var isPrimary: Boolean? = null

    @Option(
        names = ["--class-name"],
        description = ["Advanced: dotted path of a built-in evaluator class."],
        hidden = true,
    )
    private var className: String? = null

    @Option(
        names = ["--class-init-config"],
        description = ["Advanced: constructor arguments for --class-name, as a JSON object."],
        hidden = true,
    )
    private var classInitConfig: String? = null

    override fun execute(): Int {
        val evalm8 = Evalm8Options(
            org = evalm8Org,
            project = evalm8Project,
            evalName = evalm8EvalName,
            evalRef = evalm8EvalRef,
            baseUrl = evalm8BaseUrl,
            apiKey = evalm8ApiKey,
        )

        val updated = runBlocking {
            // The Evalm8 path needs the stored eval so partial flags merge onto it.
            // A class change needs it too, to tell whether the eval is leaving Evalm8 and stranding its config.
            val needsExisting = evalm8.anyProvided() || className != null || documentNamesClass()
            val existing = if (needsExisting) readExisting() else ExistingEval(null, null)

            val resolved = EvalRequestResolver(getJsonMapper()).resolveUpdate(
                evalConfig = evalConfig,
                evalConfigFile = evalConfigFile,
                name = name,
                state = state,
                isPrimary = isPrimary,
                granularity = granularity,
                className = className,
                classInitConfig = classInitConfig,
                evalm8 = evalm8,
                existing = existing,
            )

            if (resolved.className == EVALM8_CLASS_NAME && resolved.classInitConfig != null && !skipVerify) {
                verify(resolved.classInitConfig)
            }

            divyamClient.updateEval(
                orgId = getOrgId(orgId),
                serviceAccountId = getSaId(serviceAccountId),
                evalId = evalId,
                evalUpdateRequest = EvalUpdateRequest(
                    name = resolved.name,
                    granularity = resolved.granularity,
                    className = resolved.className,
                    // Null means the field is absent and the router keeps what it stored.
                    // An empty map reaches the router as {} and clears the stored config, which is how an eval moves onto a class that takes no constructor arguments.
                    classInitConfig = resolved.classInitConfig,
                    state = resolved.state,
                    isPrimary = resolved.isPrimary,
                    samplingConfig = resolved.samplingConfig,
                ),
            )
        }
        printObjs(updated)
        return 0
    }

    /**
     * Whether a config document names the evaluator class, which is the third way an update can move an eval off Evalm8.
     *
     * A document that will not parse returns false, so the resolver reports the parse error rather than this.
     */
    private fun documentNamesClass(): Boolean {
        val mapper = getJsonMapper()
        val tree = runCatching {
            when {
                evalConfig != null -> mapper.readTree(evalConfig)
                evalConfigFile?.isFile == true -> mapper.readTree(evalConfigFile)
                else -> null
            }
        }.getOrNull()
        return tree?.hasNonNull("class_name") == true
    }

    /** Reads the eval being updated, so the Evalm8 flags change one identifier without dropping the others. */
    private suspend fun readExisting(): ExistingEval {
        val stored = divyamClient.getEval(
            serviceAccountId = getSaId(serviceAccountId),
            orgId = getOrgId(orgId),
            evalId = evalId,
        )
        @Suppress("UNCHECKED_CAST")
        return ExistingEval(
            className = stored.className,
            classInitConfig = stored.classInitConfig as? Map<String, Any?>,
        )
    }

    /** The resolver has already proved these are present, so reading them back is safe. */
    private suspend fun verify(config: Map<String, Any?>) {
        Evalm8Verifier().verify(
            baseUrl = config.getValue("base_url").toString(),
            org = config.getValue("org").toString(),
            project = config.getValue("project").toString(),
            evalName = config.getValue("eval_name").toString(),
            apiKey = config.getValue("api_key").toString(),
        )
    }
}
