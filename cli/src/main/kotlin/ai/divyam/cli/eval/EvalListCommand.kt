/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
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
        names = ["-o", "--org-id"],
        description = ["Organization id to associate the eval with. If omitted, falls back to the DIVYAM_ORG_ID environment variable, then the current config file."],
    )
    var orgId: Int? = null

    @CommandLine.Option(
        names = ["--states"],
        split = ",",
        description = [$$"Optional: List of selectors with states to show. ${COMPLETION-CANDIDATES}"]
    )
    private var states: List<EvalState>? = null

    @CommandLine.Option(
        names = ["--primary-only"],
        description = [$$"Optional: Lists only primary evals."]
    )
    private var primaryOnly = false

    override fun execute(): Int {
        runBlocking {
            val sa = getServiceAccount()
            val evals =
                divyamClient.listEvals(
                    orgId = getOrgId(orgId),
                    serviceAccountId = getSaId(serviceAccountId),
                    evalState = states,
                    primaryOnly = primaryOnly
                )
            printObjs(evals)
        }
        return 0
    }
}