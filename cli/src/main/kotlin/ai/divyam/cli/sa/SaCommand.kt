package ai.divyam.cli.sa

import ai.divyam.cli.base.BaseSubCommand
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "sa",
    subcommands = [SaListCommand::class, SaGetCommand::class,
        SaCreateCommand::class,
        SaUpdateCommand::class]
)
class SaCommand : BaseSubCommand(), Callable<Int>