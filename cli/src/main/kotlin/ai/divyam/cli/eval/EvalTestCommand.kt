/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.eval

import ai.divyam.cli.base.SaSpecificCommand
import ai.divyam.data.model.EvalTestRequest
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import java.io.File

@CommandLine.Command(name = "test", description = ["Test an eval against one record."])
class EvalTestCommand : SaSpecificCommand() {
    @CommandLine.Option(
        names = ["--id"],
        description = ["The eval id to test"],
        required = true,
    )
    private var evalId: Int = 0

    @CommandLine.Option(
        names = ["-o", "--org-id"],
        description = ["Organization id. If omitted, falls back to the DIVYAM_ORG_ID environment variable, then the current config file."],
    )
    private var orgId: Int? = null

    @CommandLine.Option(
        names = ["--record-file"],
        description = ["JSON file containing one record matching the eval granularity."],
        required = true,
    )
    private lateinit var recordFile: File

    override fun execute(): Int {
        require(recordFile.exists()) {
            "Record file does not exist: ${recordFile.absolutePath}"
        }
        require(recordFile.isFile) {
            "Record path is not a file: ${recordFile.absolutePath}"
        }

        val record = getJsonMapper().readValue<Map<String, Any>>(recordFile)
        val result = runBlocking {
            divyamClient.testEval(
                serviceAccountId = getSaId(serviceAccountId),
                evalId = evalId,
                orgId = getOrgId(orgId),
                evalTestRequest = EvalTestRequest(record = record),
            )
        }
        printObjs(result)
        return 0
    }
}
