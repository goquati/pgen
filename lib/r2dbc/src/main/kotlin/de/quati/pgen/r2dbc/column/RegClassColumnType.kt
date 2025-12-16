package de.quati.pgen.r2dbc.column

import de.quati.pgen.shared.RegClass
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table

public class RegClassColumnType : ColumnType<RegClass>() {
    override fun sqlType(): String = "regclass"
    override fun notNullValueToDB(value: RegClass): Any = value.name
    override fun valueFromDB(value: Any): RegClass = RegClass.of(value as String)
}

public fun Table.regClass(name: String): Column<RegClass> {
    return registerColumn(name = name, type = RegClassColumnType())
}