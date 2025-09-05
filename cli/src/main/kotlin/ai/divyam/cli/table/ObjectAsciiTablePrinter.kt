package ai.divyam.cli.table

import de.vandermeer.asciitable.AsciiTable
import de.vandermeer.asciitable.CWC_LongestWord
import de.vandermeer.asciithemes.a8.A8_Grids
import de.vandermeer.asciithemes.u8.U8_Grids
import java.lang.reflect.Field
import java.util.Arrays
import java.util.stream.Collectors

object ObjectAsciiTablePrinter {
    fun printTable(objects: List<Any>?) {
        if (objects == null || objects.isEmpty()) {
            println("No objects to display.")
            return
        }

        // Use the first object to determine the headers (column names)
        val clazz: Class<*> = objects[0].javaClass
        val fields = clazz.getDeclaredFields()

        // Dynamically create headers
        val headers = Arrays.stream(fields)
            .map { obj: Field? -> splitCamelCase(obj!!.name) }
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

        /*table.context.setGrid(
            A8_Grids.lineDoubleBlocks()
        )*/

        table.context.setGrid(U8_Grids.borderLight())
        // Print the data rows
        for (obj in objects) {
            val rowData = Arrays.stream(fields)
                .map { field: Field? ->
                    try {
                        field!!.setAccessible(true) // Allow access to private fields
                        return@map field.get(obj)?.toString() ?: ""
                    } catch (_: IllegalAccessException) {
                        return@map "N/A"
                    }
                }
                .collect(Collectors.toList())
            table.addRow(rowData)
        }
        table.addRule()

        println(table.render(100))
    }

    private fun splitCamelCase(input: String): String {
        return input
            .split(Regex("(?=[A-Z])"))
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }
}