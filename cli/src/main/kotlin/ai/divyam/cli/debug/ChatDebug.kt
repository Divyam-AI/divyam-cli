package ai.divyam.cli.debug

import ai.divyam.cli.base.BaseCommand
import ai.divyam.cli.base.OutputFormat
import ai.divyam.cli.chat.ChatCommand.Companion.parseRawHeaders
import ai.divyam.client.asMap
import com.formkiq.graalvm.annotations.Reflectable
import io.ktor.client.call.body
import kotlinx.coroutines.runBlocking
import org.fusesource.jansi.Ansi.ansi
import picocli.CommandLine
import picocli.CommandLine.Option
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "chat",
    description = ["Invoke chat API with HTTP payload read from standard input"]
)
class ChatDebug : BaseCommand(), Callable<Int> {
    @Option(
        names = ["--mock-selector"],
        description = ["Optional: uses mock selector"],
    )
    private var isMockSelector: Boolean = false

    @Option(
        names = ["--mock-model"],
        description = ["Optional: uses mock model"],
    )
    private var isMockModel: Boolean = false

    @Option(
        names = ["--latency"],
        description = ["Optional: Measures and prints response latency"],
    )
    private var computeLatency: Boolean = false

    @Option(
        names = ["-H", "--header"],
        description = [
            "Pass custom header(s) to server",
            "Format: 'Name: Value'",
            "Can be used multiple times",
            "Examples:",
            "  -H 'Accept: application/json'",
            "  -H 'Authorization: Bearer token123'",
            "  -H 'Accept: application/json' -H 'Accept: text/plain'"
        ]
    )
    var rawHeaders: List<String> = mutableListOf()

    val customHeaders: Map<String, List<String>> by lazy {
        parseRawHeaders(rawHeaders)
    }

    override fun execute(): Int {
        // Gather input payload
        val input = generateSequence {
            readlnOrNull() // returns null at EOF
        }.joinToString("\n")

        println(ansi().fgGreen().a("please wait ...").reset())

        runBlocking {
            val (httpResponse, measuredLatency) = measureAndDisplayTime(
                computeLatency
            ) {
                divyamClient.chatCompletionPayloadMode(
                    input, customHeaders = customHeaders,
                    mockSelector = isMockSelector, mockModel = isMockModel
                )
            }

            val bodyStr = httpResponse.body<String>()

            val body = try {
                getJsonMapper().readValue(bodyStr, Object::class.java)
            } catch (_: Exception) {
                // Response is not valid json.
                bodyStr
            }

            val response = ResponseDump(
                statusCode = httpResponse.status
                    .value, headers = httpResponse.headers.asMap(), body
            )

            print(ansi().fgMagenta())
            if (outputFormat == OutputFormat.JSON) {
                printJson(response)
            } else {
                printYaml(response)
            }
            print(ansi().reset())

            if (computeLatency) {
                print(ansi().fgGreen().a("Latency: ").reset())
                println(measuredLatency)
            }
        }

        return 0
    }
}

@Reflectable
data class ResponseDump(
    val statusCode: Int, val headers: Map<String, Any>, val body: Any
)