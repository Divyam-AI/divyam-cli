/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.model

import ai.divyam.cli.base.BaseCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "ls", description = ["List model info"])
class ModelInfoListCommand : BaseCommand() {
    @Option(
        names = ["-o", "--org-id"],
        description = ["Required: Organization id to associate the model " +
                "info with. If omitted, falls back to the DIVYAM_ORG_ID environment variable, then the current config file."],
    )
    private var orgId: Int? = null

    @Option(
        names = ["-s", "--sa-id", "--service-account-id"],
        description = ["Required: service account id to filter model " +
                "info with. If omitted, falls back to the DIVYAM_SA_ID environment variable, then the current config file."],
    )
    private var serviceAccountId: String? = null

    override fun execute(): Int {
        runBlocking {
            val resolvedOrgId = getOrgId(orgId)
            val resolvedServiceAccountId = getSaId(serviceAccountId)
            val infos =
                divyamClient.listModelInfos(resolvedOrgId, resolvedServiceAccountId)
            printObjs(infos)
        }
        return 0
    }
}