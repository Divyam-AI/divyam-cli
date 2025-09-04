package ai.divyam.cli.user

import ai.divyam.cli.base.BaseCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine

@CommandLine.Command(name = "ls")
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
            val users = divyamClient.getUsers(orgId)
            printObjs(users)
        }
        return 0
    }
}