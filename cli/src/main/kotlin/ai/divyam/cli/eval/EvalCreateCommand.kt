/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.eval

import ai.divyam.cli.base.SaSpecificCommand
import ai.divyam.data.model.EvalCreateRequest
import ai.divyam.data.model.EvalGranularity
import ai.divyam.data.model.EvalState
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File

@CommandLine.Command(
    name = "create",
    description = [
        "Register an eval with the router.",
        "Most evals live in evalm8, so name it with the --evalm8-* flags and the CLI resolves the " +
            "evaluator class for you.",
        "",
        "  divyam eval create --name \"Tutor Eval\" --evalm8-org acme \\",
        "    --evalm8-project tutor --evalm8-eval-name \"Tutor Eval\" --evalm8-api-key <key>",
        "",
    ],
)
class EvalCreateCommand : SaSpecificCommand() {
    @Option(
        names = ["-o", "--org-id"],
        description = ["Organization id to associate the eval with. If omitted, falls back to the DIVYAM_ORG_ID environment variable, then the current config file."],
    )
    var orgId: Int? = null

    @Option(
        names = ["--name"],
        description = ["Required: eval name."],
    )
    private var name: String? = null

    @Option(
        names = ["--evalm8-org"],
        description = ["The evalm8 organisation holding the eval."],
    )
    private var evalm8Org: String? = null

    @Option(
        names = ["--evalm8-project"],
        description = ["The evalm8 project holding the eval."],
    )
    private var evalm8Project: String? = null

    @Option(
        names = ["--evalm8-eval-name"],
        description = ["The eval as named in evalm8."],
    )
    private var evalm8EvalName: String? = null

    @Option(
        names = ["--evalm8-eval-ref"],
        description = ["Which version of the evalm8 eval to pin. Default: latest"],
    )
    private var evalm8EvalRef: String? = null

    @Option(
        names = ["--evalm8-base-url"],
        description = ["evalm8 endpoint. Default: $EVALM8_DEFAULT_BASE_URL"],
    )
    private var evalm8BaseUrl: String? = null

    @Option(
        names = ["--evalm8-api-key"],
        description = ["evalm8 api key. This is distinct from the router api key."],
    )
    private var evalm8ApiKey: String? = null

    @Option(
        names = ["--skip-verify"],
        description = ["Register without first checking the eval exists in evalm8."],
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
        names = ["--state"],
        description = [$$"Eval state. Default: ACTIVE. ${COMPLETION-CANDIDATES}"],
    )
    private var state: EvalState? = null

    @Option(
        names = ["--is-primary"],
        description = ["Whether this is the primary eval. Default: false"],
        arity = "1",
    )
    private var isPrimary: Boolean? = null

    @Option(
        names = ["--granularity"],
        description = [$$"Optional: derived from the evaluator class when omitted. ${COMPLETION-CANDIDATES}"],
    )
    private var granularity: EvalGranularity? = null

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
        val resolved = EvalRequestResolver(getJsonMapper()).resolve(
            evalConfig = evalConfig,
            evalConfigFile = evalConfigFile,
            name = name,
            state = state,
            isPrimary = isPrimary,
            granularity = granularity,
            className = className,
            classInitConfig = classInitConfig,
            evalm8 = Evalm8Options(
                org = evalm8Org,
                project = evalm8Project,
                evalName = evalm8EvalName,
                evalRef = evalm8EvalRef,
                baseUrl = evalm8BaseUrl,
                apiKey = evalm8ApiKey,
            ),
        )

        val newEval = runBlocking {
            if (resolved.className == EVALM8_CLASS_NAME && !skipVerify) {
                verify(resolved.classInitConfig)
            }
            val sa = getServiceAccount()
            divyamClient.createEval(
                serviceAccountId = getSaId(serviceAccountId),
                orgId = getOrgId(orgId),
                evalCreateRequest = EvalCreateRequest(
                    orgId = getOrgId(orgId),
                    serviceAccountId = sa.id,
                    name = resolved.name,
                    // Null drops the key from the payload, so the server derives it from the class.
                    granularity = resolved.granularity,
                    className = resolved.className,
                    state = resolved.state,
                    classInitConfig = resolved.classInitConfig,
                    samplingConfig = resolved.samplingConfig,
                    isPrimary = resolved.isPrimary,
                ),
            )
        }
        printObjs(newEval)
        return 0
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
