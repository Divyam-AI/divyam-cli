package ai.divyam.cli.base

import ai.divyam.cli.table.ObjectAsciiTablePrinter
import ai.divyam.client.DivyamClient
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.runBlocking
import org.fusesource.jansi.Ansi.ansi
import org.fusesource.jansi.AnsiConsole
import picocli.CommandLine
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

/**
 * Output format.
 */
enum class OutputFormat {
    TEXT,
    JSON,
    YAML
}

/**
 * Base class for leaf level command.
 */
abstract class BaseCommand : Callable<Int> {
    @CommandLine.Option(
        names = ["-e", "--endpoint"],
        description = ["The API endpoint base URL (e.g. https://api.divyam" +
                ".ai)"],
        defaultValue = "https://api.divyam.ai"
    )
    lateinit var endpoint: String

    @CommandLine.Option(
        names = ["--disable-tls-verification"],
        description = ["Disable TLS verification for non-production cases " +
                "only"],
        help = false,
        hidden = true
    )
    var disableTlsVerification: Boolean = false

    // TODO: Check all option names for usability, consistency and conventions.
    @CommandLine.Option(
        names = ["-u", "--user"],
        description = ["User email-id. Required if service account api token" +
                " is not provided."],
    )
    protected var user: String? = null

    @CommandLine.Option(
        names = ["-p", "--password"],
        description = ["User password. Required if service account api token " +
                "is not provided."],
        interactive = true,
        arity = "0..1"
    )
    protected var password: String? = null

    @CommandLine.Option(
        names = ["-t", "--api-token"],
        description = ["Service account api token"],
        interactive = true,
        arity = "0..1"
    )
    protected var apiToken: String? = null

    @CommandLine.Option(
        names = ["--format"],
        description = ["output format. Valid values: \${COMPLETION-CANDIDATES}"]
    )
    protected var outputFormat: OutputFormat = OutputFormat.TEXT

    @Suppress("unused")
    @CommandLine.Option(
        names = ["--help"],
        usageHelp = true,
        description = ["display help message"]
    )
    private var helpRequested = false

    @CommandLine.Option(
        names = ["--stacktrace"],
        description = ["Show stacktrace on errors"],
        help = false,
        hidden = true
    )
    var showStackTrace: Boolean = false

    protected val divyamClient: DivyamClient by lazy(
        mode =
            LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        DivyamClient(
            endpoint = endpoint, emailId = user, password =
                password, apiToken = apiToken, disableTlsVerification =
                disableTlsVerification
        )
    }

    protected fun printObjs(objs: Any) {
        when (outputFormat) {
            OutputFormat.TEXT -> ObjectAsciiTablePrinter.printTable(
                if (objs is List<*>) {
                    @Suppress("UNCHECKED_CAST")
                    objs as List<Any>
                } else {
                    listOf(objs)
                }
            )

            OutputFormat.JSON -> printJson(objs)
            OutputFormat.YAML -> printYaml(objs)
        }
    }

    final override fun call(): Int {
        System.setProperty("jansi.force", "true")

        // Coloured output support.
        AnsiConsole.systemInstall()

        try {
            if (apiToken == null) {
                require(user != null && password != null) {
                    "One of api token or user email and password are required for" +
                            " authentication."
                }
            }
            return execute()
        } catch (e: Exception) {
            if (showStackTrace) {
                System.err.print(ansi().fgRed().a(""))
                e.printStackTrace()
                System.err.print(ansi().reset())
            } else {
                System.err.println(
                    ansi().fgRed().a(getDisplayMessage(e))
                        .reset()
                )
            }
            return 1
        }
    }

    private fun getDisplayMessage(e: Throwable?): String {
        val messageBuffer = StringBuilder()
        messageBuffer.append(e?.message ?: "")
        e?.cause?.let {
            messageBuffer.append("->")
            messageBuffer.append(getDisplayMessage(it))
        }
        return messageBuffer.toString()
    }

    abstract fun execute(): Int

    protected fun printJson(objs: Any) {
        val mapper = getJsonMapper()
        val writer = mapper.writerWithDefaultPrettyPrinter()
        println(writer.writeValueAsString(objs))
    }

    protected fun getJsonMapper(): ObjectMapper {
        val mapper = ObjectMapper()
        mapper.registerKotlinModule()
        // You can configure the Jackson ObjectMapper here
        // For example, to ignore unknown properties in the JSON response
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        return mapper
    }

    protected fun printYaml(objs: Any) {
        val mapper = getYamlMapper()
        val writer = mapper.writerWithDefaultPrettyPrinter()
        println(writer.writeValueAsString(objs))
    }

    protected fun getYamlMapper(): YAMLMapper {
        val mapper = YAMLMapper()
        mapper.registerKotlinModule()
        // You can configure the Jackson ObjectMapper here
        // For example, to ignore unknown properties in the JSON response
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        return mapper
    }

    protected fun <T> measureAndDisplayTime(
        computeLatency: Boolean,
        block: suspend () -> T
    ): Pair<T, String> {
        if (!computeLatency) {
            return runBlocking {
                block.invoke() to ""
            }
        }

        var result: T
        val durationNanos = measureNanoTime {
            result = runBlocking { block.invoke() }
        }
        val durationMillis = TimeUnit.NANOSECONDS.toMillis(durationNanos)

        val timeString = when {
            durationMillis >= TimeUnit.MINUTES.toMillis(1) -> {
                val minutes = TimeUnit.NANOSECONDS.toMinutes(durationNanos)
                val remainingSeconds =
                    TimeUnit.NANOSECONDS.toSeconds(durationNanos) % 60
                "Took $minutes min, $remainingSeconds sec"
            }

            durationMillis >= 1000 -> {
                String.format("Took %.2f sec", durationNanos / 1_000_000_000.0)
            }

            durationNanos >= 1_000_000 -> {
                "Took $durationMillis ms"
            }

            durationNanos >= 1000 -> {
                String.format("Took %.2f µs", durationNanos / 1000.0)
            }

            else -> {
                "Took $durationNanos ns"
            }
        }
        return result to timeString
    }
}