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
                "info with"],
        required = true
    )
    private var orgId: Int = 0

    @Option(
        names = ["-s", "--sa-id", "--service-account-id"],
        description = ["Optional: service account id to filter model " +
                "info with"],
    )
    private var serviceAccountId: String? = null

    override fun execute(): Int {
        runBlocking {
            val infos =
                divyamClient.listModelInfos(orgId, serviceAccountId)
            printObjs(infos)
        }
        return 0
    }
}