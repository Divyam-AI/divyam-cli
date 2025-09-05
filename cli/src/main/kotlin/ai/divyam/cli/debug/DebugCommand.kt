package ai.divyam.cli.debug

import ai.divyam.cli.base.BaseSubCommand
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "debug",
    description = ["Debugging tools"],
    subcommands = [ChatDebug::class]
)
class DebugCommand : BaseSubCommand(), Callable<Int>