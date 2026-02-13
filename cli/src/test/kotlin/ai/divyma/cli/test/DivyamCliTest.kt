/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyma.cli.test

import ai.divyam.cli.ServerCommand
import ai.divyam.cli.chat.ChatCommand
import ai.divyam.cli.eval.EvalCommand
import ai.divyam.cli.model.ModelInfoCommand
import ai.divyam.cli.org.OrgCommand
import ai.divyam.cli.sa.SaCommand
import ai.divyam.cli.selector.ModelSelectorCommand
import ai.divyam.cli.user.UserCommand
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.concurrent.thread

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DivyamCliTest {

    private lateinit var serverThread: Thread
    private val testPort = 8081
    private val testPassword = "test123"
    private val baseUrl = "http://localhost:$testPort"
    private val mapper: ObjectMapper = jacksonObjectMapper()

    private val outContent = ByteArrayOutputStream()
    private val errContent = ByteArrayOutputStream()

    private val originalOut = System.out
    private val originalErr = System.err

    // Shared test data
    private lateinit var testServiceAccountId: String

    @BeforeAll
    fun setupServer() {
        serverThread = thread(isDaemon = true) {
            CommandLine(ServerCommand()).execute(
                "--port", testPort.toString(),
                "--password", testPassword
            )
        }

        runBlocking { delay(2000) }

        // For some reason stream capture fails on the first command. Run time for the first time.
        runDummyCommand()
        createTestServiceAccount()
    }

    private fun createTestServiceAccount() {
        System.setOut(PrintStream(outContent))
        val exitCode = executeCommand(
            SaCommand(),
            "create",
            "--org-id", "1",
            "--name", "Test SA",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        testServiceAccountId = json!!.get("id").asText()
        System.setOut(originalOut)
        outContent.reset()
    }

    private fun runDummyCommand() {
        System.setOut(PrintStream(outContent))
        executeCommand(
            OrgCommand(),
            "ls",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
        )
        parseJson()
        System.setOut(originalOut)
        outContent.reset()
    }

    @AfterAll
    fun teardown() {
        serverThread.interrupt()
    }

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
        print(outContent.toString())
        System.err.println(errContent.toString())
    }

    private fun executeCommand(
        command: Any,
        vararg args: String
    ): Int {
        val cmd = CommandLine(command)
        cmd.isCaseInsensitiveEnumValuesAllowed = true
        return cmd.execute(*args)
    }

    private fun parseJson(): JsonNode? {
        return try {
            val output = outContent.toString().trim()
            if (output.isEmpty()) null else mapper.readTree(output)
        } catch (_: Exception) {
            null
        }
    }

    // ============================================
    // Organization CRUD Tests
    // ============================================

    @Test
    @Order(1)
    fun `org create`() {
        val exitCode = executeCommand(
            OrgCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--name", "Test Org",
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertTrue(json!!.has("id"))
        assertEquals("Test Org", json.get("name").asText())
    }

    @Test
    @Order(2)
    fun `org list`() {
        val exitCode = executeCommand(
            OrgCommand(),
            "ls",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        val expected = mapper.readTree(
            """
            [ {
              "name" : "Sample Org",
              "id" : 1
            }, {
              "name" : "Test Org",
              "id" : 2
            } ]
        """.trimIndent()
        )
        println("actual json: $json")
        assertEquals(expected, json)
    }

    @Test
    @Order(3)
    fun `org get`() {
        val exitCode = executeCommand(
            OrgCommand(),
            "get",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--id", "1",
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertEquals(1, json!!.get("id").asInt())
        assertEquals("Sample Org", json.get("name").asText())

    }

    @Test
    @Order(4)
    fun `org update`() {
        val exitCode = executeCommand(
            OrgCommand(),
            "update",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--id", "2",
            "--name", "Updated Org",
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertEquals("Updated Org", json!!.get("name").asText())
    }

    // ============================================
    // User CRUD Tests
    // ============================================

    @Test
    @Order(5)
    fun `user create`() {
        val email = "test.${System.currentTimeMillis()}@example.com"
        val exitCode = executeCommand(
            UserCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--org-id", "1",
            "--email", email,
            "--name", "Test User",
            "--user-password", "newpass"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertEquals(email, json!!.get("email_id").asText())
        assertEquals("Test User", json.get("name").asText())
    }

    @Test
    @Order(6)
    fun `user list`() {
        val exitCode = executeCommand(
            UserCommand(),
            "ls",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--org-id", "1"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertTrue(json!!.isArray)
    }

    @Test
    @Order(7)
    fun `user get`() {
        val email = "get.${System.currentTimeMillis()}@example.com"
        val username = "Get User"
        executeCommand(
            UserCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--org-id", "1",
            "--email", email,
            "--name", username,
            "--user-password", "newpass"
        )

        outContent.reset()

        val exitCode = executeCommand(
            UserCommand(),
            "get",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--email", email
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertEquals(email, json!!.get("email_id").asText())
        assertEquals(username, json.get("name").asText())

    }

    @Test
    @Order(8)
    fun `user update`() {
        val email = "update.${System.currentTimeMillis()}@example.com"
        executeCommand(
            UserCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--org-id", "1",
            "--email", email,
            "--name", "Original",
            "--user-password", "newpass"
        )

        outContent.reset()

        val exitCode = executeCommand(
            UserCommand(),
            "update",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--email", email,
            "--name", "Updated"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertEquals("Updated", json!!.get("name").asText())
    }

    // ============================================
    // Service Account CRUD Tests (Must come before dependent tests)
    // ============================================

    @Test
    @Order(10)
    fun `sa create`() {
        val exitCode = executeCommand(
            SaCommand(),
            "create",
            "--org-id", "1",
            "--name", "Test SA",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertTrue(json!!.has("id"))
        assertEquals("Test SA", json.get("name").asText())
    }

    @Test
    @Order(11)
    fun `sa list`() {
        val exitCode = executeCommand(
            SaCommand(),
            "ls",
            "--org-id", "1",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertTrue(json!!.isArray)
    }

    @Test
    @Order(12)
    fun `sa get`() {
        executeCommand(
            SaCommand(),
            "create",
            "--org-id", "1",
            "--name", "Get SA",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        val createJson = parseJson()
        val saId = createJson!!.get("id").asText()

        outContent.reset()

        val exitCode = executeCommand(
            SaCommand(),
            "get",
            "--id", saId,
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertEquals(saId, json!!.get("id").asText())
    }

    @Test
    @Order(13)
    fun `sa update`() {
        executeCommand(
            SaCommand(),
            "create",
            "--org-id", "1",
            "--name", "Update SA",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        val createJson = parseJson()
        val saId = createJson!!.get("id").asText()

        outContent.reset()

        val exitCode = executeCommand(
            SaCommand(),
            "update",
            "--id", saId,
            "--name", "Updated SA",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertEquals("Updated SA", json!!.get("name").asText())
    }

    // ============================================
    // Model Info CRUD Tests (Requires SA)
    // ============================================

    @Test
    @Order(20)
    fun `model-info create`() {
        val exitCode = executeCommand(
            ModelInfoCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--org-id", "1",
            "--provider-name", "openai",
            "--model-names", "gpt-4.1-mini",
            "--provider-base-url", "https://api.openai.com/v1",
            "--provider-api-key", "test-key",
            "--service-account-id", testServiceAccountId
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        val modelInfo = json!!.first()
        assertTrue(modelInfo.has("id"))
        assertEquals("openai", modelInfo.get("name_provider").asText())
        assertEquals("gpt-4.1-mini", modelInfo.get("name_model").asText())
    }

    @Test
    @Order(21)
    fun `model-info list`() {
        val exitCode = executeCommand(
            ModelInfoCommand(),
            "ls",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--org-id", "1"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertTrue(json!!.isArray)
    }

    @Test
    @Order(22)
    fun `model-info get`() {
        var exitCode = executeCommand(
            ModelInfoCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--org-id", "1",
            "--provider-name", "openai",
            "--model-names", "gpt-4.1-mini",
            "--provider-base-url", "https://api.openai.com/v1",
            "--provider-api-key", "test-key",
            "--service-account-id", testServiceAccountId
        )

        assertEquals(0, exitCode)
        var json = parseJson()
        assertNotNull(json)
        val modelInfo = json!!.first()

        val modelInfoId = modelInfo!!.get("id").asInt()

        outContent.reset()

        exitCode = executeCommand(
            ModelInfoCommand(),
            "get",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--org-id", "1",
            "--id", modelInfoId.toString(),
        )

        assertEquals(0, exitCode)
        json = parseJson()
        assertNotNull(json)
        assertEquals(modelInfoId, json!!.get("id").asInt())
    }

    @Test
    @Order(23)
    fun `model-info update`() {
        var exitCode = executeCommand(
            ModelInfoCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--org-id", "1",
            "--provider-name", "openai",
            "--model-names", "gpt-4.1-mini",
            "--provider-base-url", "https://api.openai.com/v1",
            "--provider-api-key", "test-key",
            "--service-account-id", testServiceAccountId
        )

        assertEquals(0, exitCode)
        var json = parseJson()
        assertNotNull(json)
        val modelInfo = json!!.first()

        val modelInfoId = modelInfo!!.get("id").asInt()

        outContent.reset()

        exitCode = executeCommand(
            ModelInfoCommand(),
            "update",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--org-id", "1",
            "--id", modelInfoId.toString(),
            "--model-name", "gpt-4.1-nano",
        )

        assertEquals(0, exitCode)
        json = parseJson()
        assertNotNull(json)
        assertEquals("gpt-4.1-nano", json!!.get("name_model").asText())
    }

    @Test
    @Order(24)
    fun `model-info delete`() {
        var exitCode = executeCommand(
            ModelInfoCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--org-id", "1",
            "--provider-name", "openai",
            "--model-names", "gpt-4.1-mini",
            "--provider-base-url", "https://api.openai.com/v1",
            "--provider-api-key", "test-key",
            "--service-account-id", testServiceAccountId
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        val modelInfo = json!!.first()

        val modelInfoId = modelInfo!!.get("id").asInt()

        outContent.reset()

        exitCode = executeCommand(
            ModelInfoCommand(),
            "delete",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--org-id", "1",
            "--id", modelInfoId.toString(),
        )

        assertEquals(0, exitCode)

        exitCode = executeCommand(
            ModelInfoCommand(),
            "get",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--org-id", "1",
            "--id", modelInfoId.toString(),
        )

        assertEquals(1, exitCode)
    }

    // ============================================
    // Model Selector CRUD Tests (Requires SA)
    // ============================================

    @Test
    @Order(30)
    fun `selector create`() {
        val exitCode = executeCommand(
            ModelSelectorCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--name", "Test Selector",
            "--org-id", "1",
            "--service-account-id", testServiceAccountId,
            "--extractor-strategy", "default"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertTrue(json!!.has("id"))
        assertEquals("Test Selector", json.get("name").asText())
        // State defaults to INACTIVE per API spec
        assertEquals("REQUESTED", json.get("state").asText())
    }

    @Test
    @Order(31)
    fun `selector list`() {
        val exitCode = executeCommand(
            ModelSelectorCommand(),
            "ls",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--org-id", "1"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertTrue(json!!.isArray)
    }

    @Test
    @Order(32)
    fun `selector update`() {
        val createExitCode = executeCommand(
            ModelSelectorCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--name", "Test Selector",
            "--org-id", "1",
            "--service-account-id", testServiceAccountId,
            "--extractor-strategy", "default"
        )

        assertEquals(0, createExitCode)
        val createJson = parseJson()
        val selectorId = createJson!!.get("id").asInt()

        outContent.reset()

        val exitCode = executeCommand(
            ModelSelectorCommand(),
            "update",
            "--id", selectorId.toString(),
            "--name", "Updated Selector",
            "--to-prod",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertEquals("Updated Selector", json!!.get("name").asText())
        assertEquals("PROD", json.get("state").asText())
    }

    @Test
    @Order(33)
    fun `selector delete`() {
        executeCommand(
            ModelSelectorCommand(),
            "create",
            "--name", "Delete Selector",
            "--org-id", "1",
            "--service-account-id", testServiceAccountId,
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        val createJson = parseJson()
        val selectorId = createJson!!.get("id").asInt()

        outContent.reset()

        val exitCode = executeCommand(
            ModelSelectorCommand(),
            "delete",
            "--id", selectorId.toString(),
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        assertEquals(0, exitCode)
    }


    @Test
    @Order(34)
    fun `selector create with config file`() {
        val exitCode = executeCommand(
            ModelSelectorCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--name", "Test Selector with Config File",
            "--org-id", "1",
            "--service-account-id", testServiceAccountId,
            "--config-file", "src/test/data/selector-config.json"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertTrue(json!!.has("id"))
        assertEquals("Test Selector with Config File", json.get("name").asText())
    }


    @Test
    @Order(37)
    fun `selector clone`() {
        // First create a source selector
        val createExitCode = executeCommand(
            ModelSelectorCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--name", "Source Selector For Clone",
            "--org-id", "1",
            "--service-account-id", testServiceAccountId,
            "--extractor-strategy", "default"
        )

        assertEquals(0, createExitCode)
        val createJson = parseJson()
        val sourceSelectorId = createJson!!.get("id").asInt()

        outContent.reset()

        // Clone the selector (state defaults to INACTIVE per API spec)
        val exitCode = executeCommand(
            ModelSelectorCommand(),
            "clone",
            "--org-id", "1",
            "--service-account-id", testServiceAccountId,
            "--from-id", sourceSelectorId.toString(),
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertTrue(json!!.has("id"))
        // Default cloned name should be "<source_name>_clone"
        assertEquals("Source Selector For Clone_clone", json.get("name").asText())
        // State defaults to REQUESTED per API spec (state in create request is ignored)
        assertEquals("REQUESTED", json.get("state").asText())
    }

    @Test
    @Order(38)
    fun `selector clone with custom name`() {
        // First create a source selector
        val createExitCode = executeCommand(
            ModelSelectorCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--name", "Another Source Selector",
            "--org-id", "1",
            "--service-account-id", testServiceAccountId
        )

        assertEquals(0, createExitCode)
        val createJson = parseJson()
        val sourceSelectorId = createJson!!.get("id").asInt()

        outContent.reset()

        // Clone the selector with a custom name
        val exitCode = executeCommand(
            ModelSelectorCommand(),
            "clone",
            "--org-id", "1",
            "--service-account-id", testServiceAccountId,
            "--from-id", sourceSelectorId.toString(),
            "--name", "My Custom Cloned Selector",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        println("actual json: $json")
        assertNotNull(json)
        assertTrue(json!!.has("id"))
        assertEquals("My Custom Cloned Selector", json.get("name").asText())
        // State defaults to REQUESTED per API spec (state in create request is ignored)
        assertEquals("REQUESTED", json.get("state").asText())
    }

    @Test
    @Order(39)
    fun `selector clone with invalid from-id fails`() {
        val exitCode = executeCommand(
            ModelSelectorCommand(),
            "clone",
            "--org-id", "1",
            "--service-account-id", testServiceAccountId,
            "--from-id", "999999",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        // Should fail because the source selector doesn't exist
        assertEquals(1, exitCode)
    }

    // ============================================
    // Eval CRUD Tests (Requires SA)
    // ============================================

    @Test
    @Order(40)
    fun `eval create`() {
        val exitCode = executeCommand(
            EvalCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--service-account-id", testServiceAccountId,
            "--name", "Test Eval",
            "--granularity", "LLM_REQUEST_RESPONSE",
            "--class-name", "TestEval",
            "--state", "ACTIVE"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertTrue(json!!.has("id"))
        assertEquals("Test Eval", json.get("name").asText())
    }

    @Test
    @Order(41)
    fun `eval list`() {
        executeCommand(
            EvalCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--service-account-id", testServiceAccountId,
            "--name", "Test Eval",
            "--granularity", "LLM_REQUEST_RESPONSE",
            "--class-name", "TestEval",
            "--state", "ACTIVE"
        )

        outContent.reset()

        val exitCode = executeCommand(
            EvalCommand(),
            "ls",
            "--service-account-id", testServiceAccountId,
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertTrue(json!!.isArray)
    }

    @Test
    @Order(42)
    fun `eval get`() {
        executeCommand(
            EvalCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--service-account-id", testServiceAccountId,
            "--name", "Test Eval 2",
            "--granularity", "LLM_REQUEST_RESPONSE",
            "--class-name", "TestEval",
            "--state", "ACTIVE"
        )

        val createJson = parseJson()
        val evalId = createJson!!.get("id").asInt()

        outContent.reset()

        val exitCode = executeCommand(
            EvalCommand(),
            "get",
            "--service-account-id", testServiceAccountId,
            "--id", evalId.toString(),
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertEquals("Test Eval 2", json!!.get("name").asText())
    }

    @Test
    @Order(43)
    fun `eval update`() {
        executeCommand(
            EvalCommand(),
            "create",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json",
            "--service-account-id", testServiceAccountId,
            "--name", "Test Eval 3",
            "--granularity", "LLM_REQUEST_RESPONSE",
            "--class-name", "TestEval",
            "--state", "ACTIVE"
        )

        val createJson = parseJson()
        val evalId = createJson!!.get("id").asInt()

        outContent.reset()

        val exitCode = executeCommand(
            EvalCommand(),
            "update",
            "--service-account-id", testServiceAccountId,
            "--id", evalId.toString(),
            "--name", "Updated Eval",
            "--state", "INACTIVE",
            "--endpoint", baseUrl,
            "--user", "admin@dashboard.divyam.ai",
            "--password", testPassword,
            "--format", "json"
        )

        assertEquals(0, exitCode)
        val json = parseJson()
        assertNotNull(json)
        assertEquals("Updated Eval", json!!.get("name").asText())
        assertEquals("INACTIVE", json.get("state").asText())
    }

    // ============================================
    // Chat Completion Test
    // ============================================

    @Test
    @Order(50)
    fun `chat completions`() {
        val originalIn = System.`in`
        try {
            System.setIn("Hi! Can you assist me?".byteInputStream())
            val exitCode = executeCommand(
                ChatCommand(),
                "--endpoint", baseUrl,
                "--user", "admin@dashboard.divyam.ai",
                "--password", testPassword,
                "--format", "json",
                "--model-name", "gpt-4.1-mini"
            )

            assertEquals(0, exitCode)
        } finally {
            System.setIn(originalIn)
        }
    }
}