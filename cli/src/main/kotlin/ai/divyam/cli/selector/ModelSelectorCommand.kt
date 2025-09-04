package ai.divyam.cli.selector

import ai.divyam.cli.base.BaseSubCommand
import picocli.CommandLine
import java.util.concurrent.Callable

// TODO: Get for selector.
@CommandLine.Command(
    name = "selector",
    subcommands = [ModelSelectorListCommand::class, ModelSelectorCreateCommand::class,
        ModelSelectorUpdateCommand::class]
)
class ModelSelectorCommand : BaseSubCommand(), Callable<Int>