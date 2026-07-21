/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.user

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.base.HasSecurityPolicy
import ai.divyam.data.model.IpVerificationStrategy
import ai.divyam.data.model.UserCreateRequest
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "create", description = ["Create a user"])
class UserCreateCommand : BaseCommand(), HasSecurityPolicy {
    @Option(
        names = ["-o", "--org-id"],
        description = ["Required: Organization id to associate the user with. If omitted, falls back to the DIVYAM_ORG_ID environment variable, then the current config file."],
        required = true
    )
    private var orgId: Int? = null

    @Option(
        names = ["--name"],
        description = ["New user name"],
        required = true
    )
    private lateinit var name: String

    @Option(
        names = ["--email"],
        description = ["Required: email for the user account to update"],
        required = true
    )
    lateinit var userEmail: String

    // TODO: Add reconfirmation.
    @Option(
        names = ["--user-password"],
        description = ["User password"],
        interactive = true,
        arity = "0..1",
        required = true
    )
    private var userPassword: String? = null

    @Option(
        names = ["--allowed-ip-networks"],
        description = ["Optional: Comma separated list of ip networks that " +
                "should be allowed to use this user account."],
        split = ","
    )
    override var allowedIpNetworks: List<String>? = null

    @Option(
        names = ["--blocked-ip-networks"],
        description = ["Optional: Comma separated list of ip networks that " +
                "should be blocked from using this user account."],
        split = ","
    )
    override var blockedIpNetworks: List<String>? = null

    @Option(
        names = ["--ip-verifications"],
        description = ["Optional: Comma separated list of verifications. " +
                $$"Valid values are ${COMPLETION-CANDIDATES}"],
        split = ","
    )
    override var ipVerifications: List<IpVerificationStrategy>? = null

    @Option(
        names = ["--xff-indices"],
        description = ["Optional: Comma separated list of xff indices to " +
                "extract client-ip from for verification"],
        split = ","
    )
    override var xffIndices: List<Int>? = null

    @Option(
        names = ["--is-admin"],
        arity = "1",
        description = ["Optional: indicates if this user should be considered as an admin account"],
    )
    private var isAdmin: Boolean = false

    @Option(
        names = ["--is-org-admin"],
        arity = "1",
        description = ["Optional: indicates if this user is an org admin account"],
    )
    private var isOrgAdmin: Boolean = false

    override fun execute(): Int {
        if (userPassword == null) {
            throw Exception("User password is required")
        }

        val created = runBlocking {
            divyamClient.createUser(
                orgId = getOrgId(orgId),
                userCreateRequest = UserCreateRequest(
                    orgId = getOrgId(orgId),
                    name = name,
                    isOrgAdmin = isOrgAdmin,
                    isAdmin = isAdmin,
                    securityPolicy = createSecurityPolicy(),
                    password = userPassword!!,
                    emailId = userEmail
                )
            )
        }
        printObjs(created)
        return 0
    }
}