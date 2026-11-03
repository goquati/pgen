package de.quati.pgen.r2dbc.column

import org.jetbrains.exposed.v1.core.TextColumnType

/** Reads and writes any postgres type via its text representation. */
public class TextBackedColumnType(private val sqlType: String) : TextColumnType() {
    override fun sqlType(): String = sqlType
}
