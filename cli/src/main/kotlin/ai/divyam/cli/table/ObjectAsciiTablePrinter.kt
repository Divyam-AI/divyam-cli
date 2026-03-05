/**
 * Copyright 2025 Divyam.ai
 * SPDX-License-Identifier: Apache-2.0
 */
package ai.divyam.cli.table

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
    val timestampFields = setOf("createdAt", "updatedAt")

    fun printTable(objects: List<Any>?, skipKeys: Set<String> = emptySet()) {
        if (objects == null || objects.isEmpty()) {
            println("No objects to display.")
            return
        }

        // Use the first object to determine the headers (column names)
        val clazz: Class<*> = objects[0].javaClass
        // FIX: Filter out any fields that match our skipKeys
        val fields = clazz.declaredFields
        .filter { it.name !in skipKeys }
        .toTypedArray()

        val table = if (isPrimitiveOrWrapper(clazz)) {
            createPrimitiveObjectsTable(objects)
        } else {
            createObjectsTable(fields, objects)
        }
        println(table.render(100))
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

    private fun formatValue(field: Field, value: String): String {
        try {
            if (timestampFields.contains(field.name)) {
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
        fields: Array<out Field>?, objects: List<Any>
    ): AsciiTable {
        // Dynamically create headers
        val headers = Arrays.stream(fields)
            .map { field: Field? -> splitCamelCase(field!!.name) }
            .collect(Collectors.toList())

        val table = AsciiTable()
        table.renderer.cwc = CWC_LongestWord()
        table.setPaddingLeft(2)   // left & right padding = 1

        // Print headers
        table.context.setGrid(
            A8_Grids.lineDoubleBlocks()
        )
        table.addRule()
        table.addRow(headers)
        table.addRule()

        table.context.setGrid(U8_Grids.borderLight())
        // Print the data rows
        for (obj in objects) {
            val rowData = Arrays.stream(fields).map { field: Field? ->
                try {
                    field!!.setAccessible(true) // Allow access to private fields
                    return@map formatValue(
                        field, field.get(obj)?.toString() ?: ""
                    )
                } catch (_: IllegalAccessException) {
                    return@map "N/A"
                }
            }.collect(Collectors.toList())
            table.addRow(rowData)
        }
        table.addRule()
        return table
    }
}