package ai.divyam.cli.base

import ai.divyam.cli.table.ObjectAsciiTablePrinter
import ai.divyam.client.DivyamClient
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import picocli.CommandLine
import java.util.concurrent.Callable

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
        if (apiToken == null) {
            require(user != null && password != null) {
                "One of api token or user email and password are required for" +
                        " authentication."
            }
        }
        return execute()
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
}