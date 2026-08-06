/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.table

import ai.divyam.cli.format.OutputRedactor
import de.vandermeer.asciitable.AsciiTable
import de.vandermeer.asciitable.CWC_LongestWord
import de.vandermeer.asciithemes.a8.A8_Grids
import de.vandermeer.asciithemes.u8.U8_Grids
import java.lang.reflect.Field
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Arrays
import java.util.stream.Collectors

object ObjectAsciiTablePrinter {
    val timestampFields = setOf("createdAt", "updatedAt", "revokedAt")

    /**
     * One table column, reading either a field of the printed object or, for a field named in
     * `flattenKeys`, a field of the object that field holds.
     */
    private class Column(
        val header: String,
        val fieldName: String,
        val read: (Any) -> Any?
    )

    fun printTable(
        objects: List<Any>?,
        skipKeys: Set<String> = emptySet(),
        redactKeys: Set<String> = emptySet(),
        flattenKeys: Set<String> = emptySet()
    ) {
        if (objects == null || objects.isEmpty()) {
            println("No objects to display.")
            return
        }

        // Use the first object to determine the headers (column names)
        val clazz: Class<*> = objects[0].javaClass

        val table = if (isPrimitiveOrWrapper(clazz)) {
            createPrimitiveObjectsTable(objects)
        } else {
            createObjectsTable(columnsOf(clazz, skipKeys, flattenKeys), objects, redactKeys)
        }
        println(table.render(100))
    }

    /**
     * A field named in `flattenKeys` contributes one column per field of its own type instead of a
     * single column holding that object's `toString()`. Only worth asking for when the nested type
     * is small: a dozen sub-fields leave every column too narrow to read.
     */
    private fun columnsOf(
        clazz: Class<*>,
        skipKeys: Set<String>,
        flattenKeys: Set<String>
    ): List<Column> = clazz.declaredFields
        .filter { it.name !in skipKeys }
        .flatMap { field ->
            field.isAccessible = true
            if (field.name !in flattenKeys) {
                listOf(
                    Column(splitCamelCase(field.name), field.name) { obj -> field.get(obj) }
                )
            } else {
                // skipKeys applies to the columns a flattened field contributes too, so that
                // skipping does not depend on whether a field was expanded.
                field.type.declaredFields
                    .filter { !it.isSynthetic && it.name !in skipKeys }
                    .map { nested ->
                        nested.isAccessible = true
                        Column(splitCamelCase(nested.name), nested.name) { obj ->
                            field.get(obj)?.let { nested.get(it) }
                        }
                    }
            }
        }

    private fun createPrimitiveObjectsTable(objects: List<Any>): AsciiTable {
        val table = AsciiTable()
        table.renderer.cwc = CWC_LongestWord()
        table.setPaddingLeft(2)   // left & right padding = 1

        // Print headers
        table.context.setGrid(
            A8_Grids.lineDoubleBlocks()
        )

        table.context.setGrid(U8_Grids.borderLight())

        table.addRule()
        for (obj in objects) {
            table.addRow(obj.toString())
        }
        table.addRule()

        return table
    }

    fun isPrimitiveOrWrapper(clazz: Class<*>): Boolean {
        return clazz.isPrimitive || String::class.java.isAssignableFrom(clazz) || Number::class.java.isAssignableFrom(
            clazz
        ) || Boolean::class.javaObjectType.isAssignableFrom(clazz) || Char::class.javaObjectType.isAssignableFrom(
            clazz
        ) || Enum::class.java.isAssignableFrom(clazz)
    }

    private fun formatValue(fieldName: String, value: String): String {
        try {
            if (timestampFields.contains(fieldName)) {
                return formatUnixTimestamp(value.toLong())
            }
        } catch (_: Exception) {
            // Ignore.
        }
        return value
    }

    private fun formatUnixTimestamp(
        unixTimestamp: Long,
        pattern: String = "yyyy-MM-dd HH:mm:ss",
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val instant = Instant.ofEpochSecond(unixTimestamp)
        val formatter = DateTimeFormatter.ofPattern(pattern).withZone(zone)
        return formatter.format(instant)
    }

    private fun splitCamelCase(input: String): String {
        return input.split(Regex("(?=[A-Z])"))
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    private fun createObjectsTable(
        columns: List<Column>,
        objects: List<Any>,
        redactKeys: Set<String>
    ): AsciiTable {
        val table = AsciiTable()
        table.renderer.cwc = CWC_LongestWord()
        table.setPaddingLeft(2)   // left & right padding = 1

        // Print headers
        table.context.setGrid(
            A8_Grids.lineDoubleBlocks()
        )
        table.addRule()
        table.addRow(columns.map { it.header })
        table.addRule()

        table.context.setGrid(U8_Grids.borderLight())
        // Print the data rows
        for (obj in objects) {
            val rowData = columns.map { column ->
                try {
                    val value = column.read(obj)?.toString() ?: ""
                    val redactedValue =
                        if (OutputRedactor.matchesKey(column.fieldName, redactKeys)) {
                            OutputRedactor.redactedValue
                        } else {
                            OutputRedactor.redactText(value, redactKeys)
                        }
                    formatValue(column.fieldName, redactedValue)
                } catch (_: IllegalAccessException) {
                    "N/A"
                }
            }
            table.addRow(rowData)
        }
        table.addRule()
        return table
    }
}
