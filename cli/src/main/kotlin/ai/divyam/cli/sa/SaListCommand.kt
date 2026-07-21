/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.sa

import ai.divyam.cli.base.BaseCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine

@CommandLine.Command(name = "ls", description = ["List service accounts"])
class SaListCommand : BaseCommand() {
    @CommandLine.Option(
        names = ["-o", "--org-id"],
        description = ["Required: Organization id to list service accounts for. If omitted, falls back to the DIVYAM_ORG_ID environment variable, then the current config file."],
    )
    var orgId: Int? = null

    override fun execute(): Int {
        runBlocking {
            val orgs = divyamClient.listServiceAccounts(orgId = getOrgId(orgId))
            printObjs(orgs)
        }
        return 0
    }
}