package ai.divyam.cli.org

import ai.divyam.cli.base.BaseSubCommand
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "org",
    subcommands = [OrgListCommand::class, OrgCreateCommand::class,
        OrgUpdateCommand::class, OrgGetCommand::class]
)
class OrgCommand : BaseSubCommand(), Callable<Int>