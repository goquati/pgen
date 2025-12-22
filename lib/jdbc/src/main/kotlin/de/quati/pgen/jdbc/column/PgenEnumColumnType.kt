package de.quati.pgen.jdbc.column

import de.quati.pgen.core.column.PgEnum
import de.quati.pgen.core.column.getPgEnumByLabel
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomEnumerationColumnType
import org.jetbrains.exposed.v1.core.Table
import org.postgresql.util.PGobject

public inline fun <reified T> Table.pgenEnum(
    name: String,
    sql: String,
): Column<T> where T : PgEnum, T : Enum<T> {
    val enumColumnType = CustomEnumerationColumnType(
        name = name,
        sql = sql,
        fromDb = {
            when (it) {
                is T -> it
                else -> getPgEnumByLabel(clazz = T::class, label = it.toString())
            }
        },
        toDb = {
            PGobject().apply {
                type = it.pgEnumTypeName
                value = it.pgEnumLabel
            }
        },
    )
    return registerColumn(name = name, type = enumColumnType)
}

public inline fun <reified T> Table.pgenEnumArray(
    name: String,
    sql: String,
): Column<List<T>> where T : PgEnum, T : Enum<T> {
    val enumColumnType = CustomEnumerationColumnType(
        name = "${name}_element",
        sql = sql,
        fromDb = {
            when (it) {
                is T -> it
                else -> getPgEnumByLabel(clazz = T::class, label = it.toString())
            }
        },
        toDb = {
            PGobject().apply {
                type = it.pgEnumTypeName
                value = it.pgEnumLabel
            }
        },
    )
    return array(name = name, columnType = enumColumnType)
}
