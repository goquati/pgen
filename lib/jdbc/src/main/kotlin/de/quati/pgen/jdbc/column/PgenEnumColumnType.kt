package de.quati.pgen.jdbc.column

import de.quati.pgen.shared.PgenEnum
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomEnumerationColumnType
import org.jetbrains.exposed.v1.core.Table
import org.postgresql.util.PGobject

public inline fun <reified T> Table.pgenEnumColumnType(
    name: String,
    sql: String,
): CustomEnumerationColumnType<T> where T : PgenEnum, T : Enum<T> = CustomEnumerationColumnType(
    name = name,
    sql = sql,
    fromDb = {
        when (it) {
            is T -> it
            else -> PgenEnum.getByLabel(clazz = T::class, label = it.toString())
        }
    },
    toDb = {
        PGobject().apply {
            type = it.pgenEnumTypeName
            value = it.pgenEnumLabel
        }
    },
)

public inline fun <reified T> Table.pgenEnum(
    name: String,
    sql: String,
): Column<T> where T : PgenEnum, T : Enum<T> {
    val enumColumnType = pgenEnumColumnType<T>(name = name, sql = sql)
    return registerColumn(name = name, type = enumColumnType)
}

public inline fun <reified T> Table.pgenEnumArray(
    name: String,
    sql: String,
): Column<List<T>> where T : PgenEnum, T : Enum<T> {
    val enumColumnType = pgenEnumColumnType<T>(name = "${name}_element", sql = sql)
    return array(name = name, columnType = enumColumnType)
}
