/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("JoinDeclarationAndAssignment")

package ai.divyam.cli.base

import ai.divyam.cli.config.ConfigCollection
import ai.divyam.cli.format.Printing
import ai.divyam.client.DivyamClient
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
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
abstract class BaseCommand(val preferApiToken: Boolean = false) :
    Callable<Int> {
    @CommandLine.Option(
        names = ["-e", "--endpoint"],
        description = ["The API endpoint base URL (e.g. https://api.divyam" +
                ".ai)"],
    )
    var endpointCli: String? = null

    @CommandLine.Option(
        names = ["--disable-tls-verification"],
        description = ["Disable TLS verification for non-production cases " +
                "only"],
        help = false,
        hidden = true
    )
    var disableTlsVerificationCli: Boolean? = null

    // TODO: Check all option names for usability, consistency and conventions.
    @CommandLine.Option(
        names = ["-u", "--user"],
        description = ["User email-id. Required if service account api token" +
                " is not provided."],
    )
    protected var userCli: String? = null

    @CommandLine.Option(
        names = ["-p", "--password"],
        description = ["User password. Required if service account api token " +
                "is not provided."],
        interactive = true,
        arity = "0..1"
    )
    protected var passwordCli: String? = null

    @CommandLine.Option(
        names = ["-t", "--api-token"],
        description = ["Service account api token"],
        interactive = true,
        arity = "0..1"
    )
    protected var apiTokenCli: String? = null

    @CommandLine.Option(
        names = ["--format"],
        description = [$$"output format. Valid values: ${COMPLETION-CANDIDATES}"]
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

    @CommandLine.Option(
        names = ["--authority-override"],
        description = ["Authority or HTTP Host override. Use for example to " +
                "set a specific host name and or port for proxies or " +
                "load-balancers."],
    )
    var authorityOverrides: String? = null

    /**
     * The Divyam endpoint to use, obtained after resolution from config and
     * environment.
     */
    lateinit var endpoint: String

    /**
     * The Divyam user to use, obtained after resolution from config and
     * environment.
     */
    var user: String? = null

    /**
     * The Divyam password to use, obtained after resolution from config and
     * environment.
     */
    var password: String? = null

    /**
     * The Divyam apiToken to use, obtained after resolution from config and
     * environment.
     */
    var apiToken: String? = null

    /**
     * Disables TLS verification.
     */
    var disableTlsVerification: Boolean = false

    protected val divyamClient: DivyamClient by lazy(
        mode =
            LazyThreadSafetyMode.SYNCHRONIZED
    ) {
        val apiTokenToUse = if (user != null && apiToken != null &&
            !preferApiToken
        ) {
            null
        } else {
            apiToken
        }
        DivyamClient(
            endpoint = endpoint, emailId = user, password =
                password, apiToken = apiTokenToUse, disableTlsVerification =
                disableTlsVerification, authorityOverride = authorityOverrides
        )
    }

    fun initialize() {
        endpoint = endpointCli ?: System.getenv("DIVYAM_ENDPOINT")
                ?: ConfigCollection.get().getCurrentConfig()?.endpoint
                ?: "https://api.divyam.ai"
        user = userCli ?: System.getenv("DIVYAM_USER") ?: ConfigCollection.get()
            .getCurrentConfig()?.user
        password = passwordCli ?: System.getenv("DIVYAM_PASSWORD")
                ?: ConfigCollection.get().getCurrentConfig()?.password
        apiToken = apiTokenCli ?: System.getenv("DIVYAM_API_TOKEN")
                ?: ConfigCollection.get().getCurrentConfig()?.apiToken
        disableTlsVerification =
            disableTlsVerificationCli ?: System.getenv("DIVYAM_DISABLE_TLS")
                ?.toBoolean() ?: ConfigCollection.get().getCurrentConfig()?.disableTlsVerification ?: false
    }

    final override fun call(): Int {
        // Initialize the lazy properties
        initialize()

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

    protected fun getDisplayMessage(e: Throwable?): String {
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
        Printing.printJson(objs)
    }

    protected fun getJsonMapper(): ObjectMapper {
        return Printing.getJsonMapper()
    }

    protected fun printYaml(objs: Any) {
        Printing.printYaml(objs)
    }

    protected fun getYamlMapper(): YAMLMapper {
        return Printing.getYamlMapper()
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

    protected fun printObjs(objs: Any, skipKeys: Set<String> = emptySet()) {
        Printing.printObjs(objs, outputFormat, skipKeys)
    }

    protected fun getOrgId(orgIdCli: Int?): Int {
        return orgIdCli ?: System.getenv("DIVYAM_ORG_ID")?.toIntOrNull()
        ?: ConfigCollection.get().getCurrentConfig()?.orgId
        ?: throw Exception("org id is required")
    }

    protected fun getSaId(saIdCli: String?): String {
        return saIdCli ?: System.getenv("DIVYAM_SA_ID")
        ?: ConfigCollection.get().getCurrentConfig()?.serviceAccountId
        ?: throw Exception("service account id is required")
    }
}