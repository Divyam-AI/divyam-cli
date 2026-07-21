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

@CommandLine.Command(name = "create", description = ["Create evals"])
class EvalCreateCommand : SaSpecificCommand() {
    @Option(
        names = ["-o", "--org-id"],
        description = ["Organization id to associate the eval with. If omitted, falls back to the DIVYAM_ORG_ID environment variable, then the current config file."],
    )
    var orgId: Int? = null

    @Option(
        names = ["--name"],
        description = ["New eval name"],
        required = true
    )
    private lateinit var name: String

    @Option(
        names = ["--granularity"],
        description = [$$"Required: granularity of the eval. ${COMPLETION-CANDIDATES}"],
        required = true
    )
    private lateinit var granularity: EvalGranularity

    @Option(
        names = ["--class-name"],
        description = ["Required: class name of the eval"],
        required = true
    )
    private lateinit var className: String

    @Option(
        names = ["--class-init-config"],
        description = ["Optional: class init config of the eval as a json " +
                "object"],
    )
    private var classInitConfig: String? = null

    @Option(
        names = ["--state"],
        description = [$$"Required: New eval state.  ${COMPLETION - " +
                "CANDIDATES}"],
        required = true
    )
    private lateinit var state: EvalState

    @Option(
        names = ["--is-primary"],
        description = ["Whether the eval is primary"],
        arity = "1"
    )
    private var isPrimary: Boolean? = null

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
            val sa = getServiceAccount()
            divyamClient.createEval(
                serviceAccountId = getSaId(serviceAccountId),
                orgId = getOrgId(orgId),
                evalCreateRequest = EvalCreateRequest(
                    orgId = getOrgId(orgId),
                    serviceAccountId = sa.id,
                    name = name,
                    granularity = granularity,
                    className = className,
                    state = state,
                    classInitConfig = classInitConfigMap,
                    isPrimary = isPrimary
                )
            )
        }
        printObjs(newEval)
        return 0
    }
}