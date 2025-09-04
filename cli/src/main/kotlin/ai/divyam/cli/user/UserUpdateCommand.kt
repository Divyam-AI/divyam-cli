package ai.divyam.cli.user

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.base.HasSecurityPolicy
import ai.divyam.client.IpVerificationStrategy
import ai.divyam.client.User
import ai.divyam.client.UserUpdateRequest
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "update", description = ["Update a user"])
class UserUpdateCommand : BaseCommand(), HasSecurityPolicy {
    @Option(
        names = ["--name"],
        description = ["Optional: New user name if change is desired"],
    )
    private var name: String? = null

    @Option(
        names = ["--email"],
        description = ["Required: email for the user account to update"],
        required = true
    )
    lateinit var userEmail: String

    // TODO: Add reconfirmation.
    @Option(
        names = ["--user-password"],
        description = ["New user password if change is desired"],
        interactive = true,
        arity = "0..1"
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
    private var isAdmin: Boolean? = null

    @Option(
        names = ["--is-org-admin"],
        arity = "1",
        description = ["Optional: indicates if this user is an org admin account"],
    )
    private var isOrgAdmin: Boolean? = null

    override fun execute(): Int {
        val updated = runBlocking {
            val user = getAndUpdateLocalUser()
            divyamClient.updateUser(
                emailId = userEmail,
                updateRequest = UserUpdateRequest(
                    name = user.name,
                    isOrgAdmin = user.isOrgAdmin,
                    isAdmin = user.isAdmin,
                    securityPolicy = user.securityPolicy,
                    password = userPassword,
                )
            )
        }
        printObjs(updated)
        return 0
    }

    /**
     * Fetches a user, applies updates from command-line arguments, and returns the updated object.
     * * @param args The command-line arguments containing the update values.
     * @return The locally updated User object.
     */
    fun getAndUpdateLocalUser(): User {
        var user = runBlocking {
            divyamClient.getUser(emailId = userEmail)
        }

        name?.let { user = user.copy(name = it.trim()) }
        isAdmin?.let { user = user.copy(isAdmin = it) }
        isOrgAdmin?.let { user = user.copy(isOrgAdmin = it) }

        user = updateObjectSecurityPolicyFromArgs(obj = user)
        return user
    }

    /**
     * Updates the security policy of an object based on command-line arguments.
     * @param obj The object whose security policy is to be updated.
     */
    fun updateObjectSecurityPolicyFromArgs(obj: User): User {
        val updatedUser =
            obj.copy(securityPolicy = updateSecurityPolicyFromArgs(obj.securityPolicy))

        return updatedUser
    }
}