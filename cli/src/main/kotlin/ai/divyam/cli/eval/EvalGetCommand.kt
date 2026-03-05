/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.eval

import ai.divyam.cli.base.SaSpecificCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "get", description = ["Get a specific eval"])
class EvalGetCommand : SaSpecificCommand() {
    @Option(
        names = ["--id"],
        description = ["The eval id to get"],
        required = true
    )
    var evalId: Int = 0

    @Option(
        names = ["-o", "--org-id"],
        description = ["Organization id to associate the eval with. If omitted, falls back to the DIVYAM_ORG_ID environment variable, then the current config file."],
    )
    var orgId: Int? = null

    override fun execute(): Int {
        val newEval = runBlocking {
            divyamClient.getEval(
                serviceAccountId = getSaId(serviceAccountId),
                orgId = getOrgId(orgId),
                evalId = evalId
            )
        }
        printObjs(newEval)
        return 0
    }
}