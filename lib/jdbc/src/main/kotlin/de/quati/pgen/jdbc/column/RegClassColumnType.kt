package de.quati.pgen.jdbc.column

import de.quati.pgen.shared.RegClass
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.postgresql.util.PGobject

public class RegClassColumnType : ColumnType<RegClass>() {
    override fun sqlType(): String = "regclass"
    override fun notNullValueToDB(value: RegClass): Any = value.name
    override fun valueFromDB(value: Any): RegClass = when(value) {
        is String -> RegClass.of(value)
        is PGobject -> RegClass.of(value.value!!)
        else -> RegClass.of(value.toString())
    }
}

public fun Table.regClass(name: String): Column<RegClass> {
    return registerColumn(name = name, type = RegClassColumnType())
}