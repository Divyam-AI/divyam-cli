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
        description = ["Required: organization id to get model selectors for. If omitted, falls back to the DIVYAM_ORG_ID environment variable, then the current config file."],
    )
    private var orgId: Int? = null

    @CommandLine.Option(
        names = ["-s", "--sa-id", "--service-account-id"],
        description = ["Required: service account id to query for. If omitted, falls back to the DIVYAM_SA_ID environment variable, then the current config file."],
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
            val resolvedOrgId = getOrgId(orgId)
            val resolvedServiceAccountId = getSaId(serviceAccountId)
            val selectors =
                divyamClient.listModelSelectors(
                    orgId = resolvedOrgId,
                    serviceAccountId = resolvedServiceAccountId,
                    modelSelectorState = states
                )
            printObjs(selectors, skipKeys = setOf("config"))
        }
        return 0
    }
}