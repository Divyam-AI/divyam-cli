/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine

@CommandLine.Command(name = "get", description = ["Get selector by id"])
class ModelSelectorGetCommand : BaseCommand() {
    @CommandLine.Option(
        names = ["--id"],
        description = ["Required: model selector id to get"],
        required = true
    )
    private var id: Int = 0

    @CommandLine.Option(
        names = ["-o", "--org-id"],
        description = ["Required: organization id to get model selector for. If omitted, falls back to the DIVYAM_ORG_ID environment variable, then the current config file."],
    )
    private var orgId: Int? = null

    override fun execute(): Int {
        runBlocking {
            val resolvedOrgId = getOrgId(orgId)
            val selector =
                divyamClient.getModelSelectorById(
                    orgId = resolvedOrgId,
                    modelSelectorId = id
                )
            printObjs(selector, skipKeys = setOf("config"))
        }
        return 0
    }
}