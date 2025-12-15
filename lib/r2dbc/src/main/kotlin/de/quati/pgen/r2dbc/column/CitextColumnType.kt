package de.quati.pgen.r2dbc.column

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType

public class CitextColumnType : TextColumnType() {
    override fun sqlType(): String = "CITEXT"
}

public fun Table.citext(name: String): Column<String> = registerColumn(name = name, type = CitextColumnType())
