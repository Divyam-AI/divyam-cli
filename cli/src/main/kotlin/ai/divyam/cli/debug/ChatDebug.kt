package ai.divyam.cli.debug

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.base.OutputFormat
import ai.divyam.client.asMap
import com.formkiq.graalvm.annotations.Reflectable
import io.ktor.client.call.body
import kotlinx.coroutines.runBlocking
import org.fusesource.jansi.AnsiConsole
import picocli.CommandLine
import java.util.Scanner
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "chat",
    description = ["Invoke chat API with HTTP payload read from standard input"]
)
class ChatDebug : BaseCommand(), Callable<Int> {
    @CommandLine.Option(
        names = ["--mock-selector"],
        description = ["Optional: uses mock selector"],
    )
    private var isMockSelector: Boolean = false

    @CommandLine.Option(
        names = ["--mock-model"],
        description = ["Optional: uses mock model"],
    )
    private var isMockModel: Boolean = false

    override fun execute(): Int {
        // Coloured output support.
        AnsiConsole.systemInstall()

        // Gather input payload
        val scanner = Scanner(System.`in`)
        val stringBuilder = StringBuilder()
        while (true) {
            val userInput: String? =
                if (scanner.hasNextLine()) {
                    scanner.nextLine()
                } else {
                    null
                }

            if (userInput == null) {
                break
            }

            stringBuilder.append(userInput)
        }
        scanner.close()

        System.err.println("please wait ...")
        runBlocking {
            val httpResponse = divyamClient.chatCompletionPayloadMode(
                stringBuilder.toString(),
                mockSelector = isMockSelector, mockModel = isMockModel
            )

            val response = ResponseDump(
                statusCode = httpResponse.status
                    .value, headers = httpResponse.headers.asMap(), httpResponse
                    .body<String>()
            )

            if (outputFormat == OutputFormat.JSON) {
                printJson(response)
            } else {
                printYaml(response)
            }
        }

        return 0
    }
}

@Reflectable
data class ResponseDump(
    val statusCode: Int, val headers: Map<String, Any>, val body: Any
)