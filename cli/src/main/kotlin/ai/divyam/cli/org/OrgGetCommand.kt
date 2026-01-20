/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.org

import ai.divyam.cli.base.BaseCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "get", description = ["Get a specific org"])
class OrgGetCommand : BaseCommand() {
    // TODO: id or name as search for all object types?
    @Option(
        names = ["--id"],
        description = ["The org id to get"],
        required = true
    )
    var orgId: Int = 0

    override fun execute(): Int {
        runBlocking {
            val orgs = divyamClient.getOrg(orgId)
            printObjs(orgs)
        }
        return 0
    }
}