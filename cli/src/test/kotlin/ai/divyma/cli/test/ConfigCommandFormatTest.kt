/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyma.cli.test

import ai.divyam.cli.config.Config
import ai.divyam.cli.config.ConfigCollection
import ai.divyam.cli.config.ConfigCurrentCommand
import ai.divyam.cli.config.ConfigGetCommand
import ai.divyam.cli.config.ConfigListCommand
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files

class ConfigCommandFormatTest {
    private val mapper = jacksonObjectMapper()
    private val yamlMapper = YAMLMapper()
    private val outContent = ByteArrayOutputStream()
    private val errContent = ByteArrayOutputStream()
    private val originalOut = System.out
    private val originalErr = System.err
    private val originalUserHome: String = System.getProperty("user.home")

    @BeforeEach
    fun setUpStreams() {
        outContent.reset()
        System.setOut(PrintStream(outContent))
        errContent.reset()
        System.setErr(PrintStream(errContent))
    }

    @AfterEach
    fun restoreStreams() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        System.setProperty("user.home", originalUserHome)
    }

    @Test
    fun `config current defaults to json`() {
        withTempHome {
            writeConfigCollection(
                currentConfigName = "test-config",
                configs = mutableMapOf(
                    "test-config" to Config(
                        endpoint = "https://api.divyam.ai",
                        user = "admin@example.com",
                        orgId = 7,
                        serviceAccountId = "sa-123"
                    )
                )
            )

            val exitCode = executeCommand(ConfigCurrentCommand())

            assertEquals(0, exitCode)
            val json = parseJson()
            assertNotNull(json)
            assertEquals("test-config", json!!.get("currentConfigName").asText())
            assertEquals("https://api.divyam.ai", json.get("config").get("endpoint").asText())
            assertEquals(7, json.get("config").get("orgId").asInt())
        }
    }

    @Test
    fun `config get defaults to json`() {
        withTempHome {
            writeConfigCollection(
                configs = mutableMapOf(
                    "test-config" to Config(
                        endpoint = "https://cluster.example",
                        apiToken = "token-123",
                        disableTlsVerification = true
                    )
                )
            )

            val exitCode = executeCommand(
                ConfigGetCommand(),
                "--config-name", "test-config"
            )

            assertEquals(0, exitCode)
            val json = parseJson()
            assertNotNull(json)
            assertEquals("https://cluster.example", json!!.get("endpoint").asText())
            assertEquals("token-123", json.get("apiToken").asText())
            assertTrue(json.get("disableTlsVerification").asBoolean())
        }
    }

    @Test
    fun `config list defaults to json`() {
        withTempHome {
            writeConfigCollection(
                configs = mutableMapOf(
                    "alpha" to Config(endpoint = "https://alpha.example"),
                    "beta" to Config(endpoint = "https://beta.example")
                )
            )

            val exitCode = executeCommand(ConfigListCommand())

            assertEquals(0, exitCode)
            val json = parseJson()
            assertNotNull(json)
            val values = json!!.map { it.asText() }.toSet()
            assertEquals(setOf("alpha", "beta"), values)
        }
    }

    @Test
    fun `config current supports text output`() {
        withTempHome {
            writeConfigCollection(
                currentConfigName = "test-config",
                configs = mutableMapOf(
                    "test-config" to Config(endpoint = "https://text.example")
                )
            )

            val exitCode = executeCommand(
                ConfigCurrentCommand(),
                "--format", "text"
            )

            assertEquals(0, exitCode)
            val output = outContent.toString().trim()
            assertTrue(output.startsWith("Current config: test-config"))
            assertTrue(output.contains("https://text.example"))
            assertFalse(output.startsWith("{"))
        }
    }

    @Test
    fun `config get supports yaml output`() {
        withTempHome {
            writeConfigCollection(
                configs = mutableMapOf(
                    "test-config" to Config(
                        endpoint = "https://yaml.example",
                        orgId = 11
                    )
                )
            )

            val exitCode = executeCommand(
                ConfigGetCommand(),
                "--config-name", "test-config",
                "--format", "yaml"
            )

            assertEquals(0, exitCode)
            val yaml = parseYaml()
            assertNotNull(yaml)
            assertEquals("https://yaml.example", yaml!!.get("endpoint").asText())
            assertEquals(11, yaml.get("orgId").asInt())
        }
    }

    private fun executeCommand(command: Any, vararg args: String): Int {
        val cmd = CommandLine(command)
        cmd.isCaseInsensitiveEnumValuesAllowed = true
        return cmd.execute(*args)
    }

    private fun parseJson(): JsonNode? {
        val output = outContent.toString().trim()
        return if (output.isEmpty()) null else mapper.readTree(output)
    }

    private fun parseYaml(): JsonNode? {
        val output = outContent.toString().trim()
        return if (output.isEmpty()) null else yamlMapper.readTree(output)
    }

    private fun withTempHome(block: () -> Unit) {
        val tempHome = Files.createTempDirectory("divyam-config-test-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        try {
            block()
        } finally {
            System.setProperty("user.home", originalUserHome)
            tempHome.deleteRecursively()
        }
    }

    private fun writeConfigCollection(
        configs: MutableMap<String, Config>,
        currentConfigName: String? = null
    ) {
        ConfigCollection.configFolder.toFile().mkdirs()
        ConfigCollection(
            configs = configs,
            currentConfigName = currentConfigName
        ).save()
    }
}
