package ai.divyam.cli

import ai.divyam.cli.chat.ChatCommand
import ai.divyam.cli.eval.EvalCommand
import ai.divyam.cli.model.ModelInfoCommand
import ai.divyam.cli.org.OrgCommand
import ai.divyam.cli.sa.SaCommand
import ai.divyam.cli.selector.ModelSelectorCommand
import ai.divyam.cli.user.UserCommand
import picocli.CommandLine
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@CommandLine.Command(
    name = "divyam",
    subcommands = [OrgCommand::class, SaCommand::class, EvalCommand::class,
        ModelSelectorCommand::class, ModelInfoCommand::class,
        UserCommand::class, ChatCommand::class]
)
class DivyamCliMain : Callable<Int> {
    @CommandLine.Option(
        names = ["--help"],
        usageHelp = true,
        description = ["display help message"]
    )
    private var helpRequested = false

    override fun call(): Int {
        CommandLine(this).usage(System.out)
        return 1
    }
}

fun main(args: Array<String>) {
    val rc =
        CommandLine(DivyamCliMain()).setCaseInsensitiveEnumValuesAllowed(true)
            .execute(*args)
    exitProcess(rc)
}