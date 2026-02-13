package de.quati.pgen.r2dbc.column

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.UuidColumnType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid


@OptIn(ExperimentalUuidApi::class)
public fun Table.pgenUuidArray(
    name: String,
): Column<List<Uuid>> {
    return registerColumn(name = name, type = PgenUuidArrayColumnType())
}

@OptIn(ExperimentalUuidApi::class)
public class PgenUuidArrayColumnType<R : List<Any?>> : ColumnType<R>() {
    private val delegate = UuidColumnType()
    override fun sqlType(): String = delegate.sqlType() + "[]"

    @Suppress("UNCHECKED_CAST")
    override fun notNullValueToDB(value: R): Any {
        val list = value as List<Uuid>
        return list.map { it.toJavaUuid() }.toTypedArray()
    }

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): R? = when (value) {
        is Array<*> -> (value.map { (it as java.util.UUID).toKotlinUuid() } as List<Any?>) as R
        is java.sql.Array -> {
            val arr = value.array as Array<*>
            (arr.map { (it as java.util.UUID).toKotlinUuid() } as List<Any?>) as R
        }
        else -> error("Unexpected value for uuid[]: ${value::class.qualifiedName}")
    }

    // these are used for SQL literals, not for prepared binds
    override fun nonNullValueToString(value: R): String =
        (value as List<*>).joinToString(",", "ARRAY[", "]") { "'$it'::uuid" }

    override fun nonNullValueAsDefaultString(value: R): String =
        nonNullValueToString(value)
}
