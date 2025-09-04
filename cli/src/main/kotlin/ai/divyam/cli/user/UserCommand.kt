package ai.divyam.cli.user

import ai.divyam.cli.base.BaseSubCommand
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "user",
    subcommands = [UserListCommand::class, UserCreateCommand::class,
        UserUpdateCommand::class, UserGetCommand::class]
)
class UserCommand : BaseSubCommand(), Callable<Int>