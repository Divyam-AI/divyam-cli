/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.user

import ai.divyam.cli.base.BaseCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine

@CommandLine.Command(name = "ls", description = ["List users"])
class UserListCommand : BaseCommand() {
    // TODO: List user's across all orgs? Need to fix this model.
    @CommandLine.Option(
        names = ["-o", "--org-id"],
        description = ["The org id"],
        required = true
    )
    var orgId: Int = 0

    override fun execute(): Int {
        runBlocking {
            val users = divyamClient.listOrgUsers(orgId)
            printObjs(users)
        }
        return 0
    }
}