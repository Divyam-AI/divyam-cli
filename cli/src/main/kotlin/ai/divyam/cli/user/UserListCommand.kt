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
        description = ["Required: Organization id to list users for. If omitted, falls back to the DIVYAM_ORG_ID environment variable, then the current config file."],
        required = true
    )
    var orgId: Int? = null

    override fun execute(): Int {
        runBlocking {
            val resolvedOrgId = getOrgId(orgId)
            val users = divyamClient.listOrgUsers(resolvedOrgId)
            printObjs(users)
            }
            return 0
        }
    }