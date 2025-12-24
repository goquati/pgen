package de.quati.pgen.r2dbc.column

import de.quati.pgen.core.column.PgEnum
import de.quati.pgen.core.column.getPgEnumByLabel
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.CustomEnumerationColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.api.RowApi


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
        toDb = { it },
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
        toDb = { it },
    )
    return registerColumn(name = name, type = PgenEnumArrayColumnType(enumColumnType))
}


public class PgenEnumArrayColumnType<T, R : List<Any?>>(
    public val delegate: CustomEnumerationColumnType<T>,
) : ColumnType<R>() where T : PgEnum, T : Enum<T> {

    override fun sqlType(): String = delegate.sqlType() + "[]"

    @Suppress("UNCHECKED_CAST")
    override fun notNullValueToDB(value: R): Array<Any?> =
        (value as List<T>).map { it.let { delegate.notNullValueToDB(it) } }.toTypedArray()

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): R? {
        return when (value) {
            is Array<*> -> recursiveValueFromDB(value.toList()) as R?
            is List<*> -> recursiveValueFromDB(value) as R?
            is java.sql.Array -> recursiveValueFromDB((value.array as Array<*>).toList()) as R?
            is String -> {
                if (!value.startsWith("{")) error("Invalid array literal: $value")
                val elements = if (value == "{}")
                    emptyList()
                else
                    value.removePrefix("{").removeSuffix("}").split(",")
                recursiveValueFromDB(elements) as R?
            }

            else -> value as R?
        }
    }

    private fun recursiveValueFromDB(value: List<*>): List<Any?> =
        value.map { it?.let { delegate.valueFromDB(it) } }

    override fun readObject(rs: RowApi, index: Int): Any? = rs.getObject(index)

    override fun nonNullValueToString(value: R): String {
        @Suppress("UNCHECKED_CAST")
        return ARRAY_LITERAL_PREFIX + (value as List<T>).joinToString(",", "[", "]") {
            it.let { delegate.nonNullValueToString(it) }
        }
    }

    override fun nonNullValueAsDefaultString(value: R): String {
        @Suppress("UNCHECKED_CAST")
        return ARRAY_LITERAL_PREFIX + (value as List<T>).joinToString(",", "[", "]") {
            it.let { delegate.nonNullValueAsDefaultString(it) }
        }
    }

    private companion object {
        private const val ARRAY_LITERAL_PREFIX = "ARRAY"
    }
}
