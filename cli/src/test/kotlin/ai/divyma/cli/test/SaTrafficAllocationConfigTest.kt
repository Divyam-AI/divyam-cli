/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyma.cli.test

import ai.divyam.cli.ServerCommand
import ai.divyam.cli.org.OrgCommand
import ai.divyam.cli.sa.SaCommand
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.concurrent.thread

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SaTrafficAllocationConfigTest {
    private lateinit var serverThread: Thread
    private val testPort = 8082
    private val testPassword = "test123"
    private val baseUrl = "http://localhost:$testPort"
    private val outContent = ByteArrayOutputStream()
    private val originalOut = System.out

    @BeforeAll
    fun setupServer() {
        serverThread = thread(isDaemon = true) {
            CommandLine(ServerCommand()).execute(
                "--port", testPort.toString(),
                "--password", testPassword
            )
        }
        runBlocking { delay(2000) }
        warmUpConsole()
    }

    @AfterAll
    fun teardown() {
        serverThread.interrupt()
    }

    @BeforeEach
    fun setUpStreams() {
        outContent.reset()
        System.setOut(PrintStream(outContent))
    }

    @AfterEach
    fun restoreStreams() {
        System.setOut(originalOut)
    }

    @Test
    fun `sa create accepts integral traffic allocation config`() {
        val exitCode = executeCommand(
            "create",
            "--org-id", "1",
            "--name", "SA With Integral Traffic Allocation",
            "--traffic-allocation-config", "{\"control\":10,\"selector_disabled\":90}",
        )

        assertEquals(0, exitCode)
        assertAllocation(parseJson(), control = 10.0, selectorDisabled = 90.0)
    }

    @Test
    fun `sa create accepts decimal traffic allocation config`() {
        val exitCode = executeCommand(
            "create",
            "--org-id", "1",
            "--name", "SA With Decimal Traffic Allocation",
            "--traffic-allocation-config", "{\"control\":10.5,\"selector_disabled\":89.5}",
        )

        assertEquals(0, exitCode)
        assertAllocation(parseJson(), control = 10.5, selectorDisabled = 89.5)
    }

    @Test
    fun `sa update accepts integral traffic allocation config`() {
        val createExitCode = executeCommand(
            "create",
            "--org-id", "1",
            "--name", "SA To Update Integral Traffic Allocation",
        )
        assertEquals(0, createExitCode)
        val serviceAccountId = parseJson()!!.get("id").asText()
        outContent.reset()

        val updateExitCode = executeCommand(
            "update",
            "--id", serviceAccountId,
            "--traffic-allocation-config", "{\"control\":10,\"selector_disabled\":90}",
        )

        assertEquals(0, updateExitCode)
        assertAllocation(parseJson(), control = 10.0, selectorDisabled = 90.0)
    }

    @Test
    fun `sa update accepts decimal traffic allocation config`() {
        val createExitCode = executeCommand(
            "create",
            "--org-id", "1",
            "--name", "SA To Update Decimal Traffic Allocation",
        )
        assertEquals(0, createExitCode)
        val serviceAccountId = parseJson()!!.get("id").asText()
        outContent.reset()

        val updateExitCode = executeCommand(
            "update",
            "--id", serviceAccountId,
            "--traffic-allocation-config", "{\"control\":10.5,\"selector_disabled\":89.5}",
        )

        assertEquals(0, updateExitCode)
        assertAllocation(parseJson(), control = 10.5, selectorDisabled = 89.5)
    }

    private fun executeCommand(vararg args: String): Int {
        val command = CommandLine(SaCommand())
        command.isCaseInsensitiveEnumValuesAllowed = true
        return command.execute(
            *args,
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )
    }

    private fun warmUpConsole() {
        System.setOut(PrintStream(outContent))
        try {
            val command = CommandLine(OrgCommand())
            command.isCaseInsensitiveEnumValuesAllowed = true
            command.execute(
                "ls",
                "--endpoint", baseUrl,
                "--user", "admin@dashboard.divyam.ai",
                "--password", testPassword,
                "--format", "json"
            )
            parseJson()
        } finally {
            System.setOut(originalOut)
            outContent.reset()
        }
    }

    private fun parseJson(): JsonNode? {
        val output = outContent.toString().trim()
        return if (output.isEmpty()) null else jacksonObjectMapper().readTree(output)
    }

    private fun assertAllocation(json: JsonNode?, control: Double, selectorDisabled: Double) {
        assertNotNull(json)
        val allocation = json!!.get("traffic_allocation_config")
        assertEquals(control, allocation.get("control").asDouble())
        assertEquals(selectorDisabled, allocation.get("selector_disabled").asDouble())
    }
}
