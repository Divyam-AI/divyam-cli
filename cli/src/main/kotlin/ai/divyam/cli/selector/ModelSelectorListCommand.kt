/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseCommand
import ai.divyam.data.model.ModelSelectorState
import kotlinx.coroutines.runBlocking
import picocli.CommandLine

@CommandLine.Command(name = "ls", description = ["List selectors"])
class ModelSelectorListCommand : BaseCommand() {
    @CommandLine.Option(
        names = ["-o", "--org-id"],
        description = ["Required: organization id to get model selectors for"],
        required = true
    )
    private var orgId: Int = 0

    @CommandLine.Option(
        names = ["-s", "--sa-id", "--service-account-id"],
        description = ["Optional: service account id to query for"],
    )
    private var serviceAccountId: String? = null

    @CommandLine.Option(
        names = ["--states"],
        split = ",",
        description = [$$"Optional: List of selectors with states to show. ${COMPLETION-CANDIDATES}"]
    )
    private var states: List<ModelSelectorState>? = null

    override fun execute(): Int {
        runBlocking {
            val selectors =
                divyamClient.listModelSelectors(
                    orgId = orgId,
                    serviceAccountId = serviceAccountId,
                    modelSelectorState = states
                )
            printObjs(selectors)
        }
        return 0
    }
}