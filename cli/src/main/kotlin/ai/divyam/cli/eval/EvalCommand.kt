package ai.divyam.cli.eval

import ai.divyam.cli.base.BaseSubCommand
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "eval",
    subcommands = [EvalListCommand::class, EvalCreateCommand::class,
        EvalUpdateCommand::class, EvalGetCommand::class]
)
class EvalCommand : BaseSubCommand(), Callable<Int>