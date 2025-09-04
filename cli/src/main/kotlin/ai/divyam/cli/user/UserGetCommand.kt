package ai.divyam.cli.user

import ai.divyam.cli.base.BaseCommand
import kotlinx.coroutines.runBlocking
import picocli.CommandLine
import picocli.CommandLine.Option

@CommandLine.Command(name = "get")
class UserGetCommand : BaseCommand() {
    // TODO: How will user be part of multiple orgs if email is globally unique?
    @Option(
        names = ["--email"],
        description = ["The email of the user to get"],
        required = true
    )
    lateinit var emailId: String

    override fun execute(): Int {
        runBlocking {
            val user = divyamClient.getUser(emailId)
            printObjs(user)
        }
        return 0
    }
}