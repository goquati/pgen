package de.quati.pgen.r2dbc.column

import de.quati.pgen.shared.PgenEnum
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomEnumerationColumnType
import org.jetbrains.exposed.v1.core.Table

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
    toDb = { it },
)

public inline fun <reified T> Table.pgenEnum(
    name: String,
    sql: String,
): Column<T> where T : PgenEnum, T : Enum<T> {
    val enumColumnType = pgenEnumColumnType<T>(name = name, sql = sql)
    return registerColumn(name = name, type = enumColumnType)
}
