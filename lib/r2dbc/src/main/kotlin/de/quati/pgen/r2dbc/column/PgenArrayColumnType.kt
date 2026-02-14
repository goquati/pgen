package de.quati.pgen.r2dbc.column

import de.quati.pgen.core.column.DomainColumnType
import de.quati.pgen.shared.PgenEnum
import io.r2dbc.postgresql.codec.PostgresqlObjectId
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.UuidColumnType
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


@OptIn(ExperimentalUuidApi::class)
public fun Table.pgenUuidArray(
    name: String,
): Column<List<Uuid>> {
    val delegate = UuidColumnType()
    return registerColumn(
        name = name,
        type = PgenArrayColumnType(
            delegate = delegate,
            oid = PostgresqlObjectId.UUID_ARRAY,
            dbClassType = Array<UUID>::class,
            toDbMapper = { value -> value.map { it as UUID }.toTypedArray() },
        ),
    )
}

public inline fun <reified T> Table.pgenEnumArray(
    name: String,
    sql: String,
): Column<List<T>> where T : PgenEnum, T : Enum<T> {
    val delegate = pgenEnumColumnType<T>(name = "${name}_element", sql = sql)
    return registerColumn(
        name = name,
        type = PgenArrayColumnType(
            delegate = delegate,
            oid = PostgresqlObjectId.UNSPECIFIED,
            dbClassType = Array<String>::class,
            toDbMapper = { value ->
                value.joinToString(separator = ",", prefix = "{", postfix = "}") {
                    (it as PgenEnum).pgenEnumLabel
                }
            },
        ),
    )
}

public inline fun <reified T : Any, R> Table.pgenDomainArray(
    name: String,
    sqlType: String,
    originType: IColumnType<R>,
    noinline builder: (R?) -> T,
): Column<List<T>> {
    val delegate = DomainColumnType.create(sqlType = sqlType, originType = originType, builder = builder)
    return registerColumn(
        name = name,
        type = PgenArrayColumnType(
            delegate = delegate,
            oid = when (originType) {
                is UuidColumnType -> PostgresqlObjectId.UUID_ARRAY
                else -> PostgresqlObjectId.TEXT_ARRAY
            },
            dbClassType = Array<Any>::class,
            toDbMapper = when (originType) {
                is UuidColumnType -> { value -> value.map { it as UUID }.toTypedArray() }
                else -> { value -> value.map { it.toString() }.toTypedArray() }
            },
        ),
    )
}

public class PgenArrayColumnType<T, R : List<Any?>>(
    public val delegate: ColumnType<T>,
    internal val oid: PostgresqlObjectId,
    internal val dbClassType: KClass<*>,
    private val toDbMapper: (List<Any>) -> Any,
) : ColumnType<R>() {
    override fun sqlType(): String = delegate.sqlType() + "[]"

    @Suppress("UNCHECKED_CAST")
    override fun notNullValueToDB(value: R): Any {
        val value = value.map { delegate.notNullValueToDB(it as (T & Any)) }
        return toDbMapper(value)
    }

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): R? = when (value) {
        is Array<*> -> recursiveValueFromDB(value.toList())
        is List<*> -> recursiveValueFromDB(value)
        is java.sql.Array -> recursiveValueFromDB((value.array as Array<*>).toList())
        else -> error("Unexpected value for ${sqlType()}: ${value::class.qualifiedName}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun recursiveValueFromDB(value: List<*>): R? =
        value.map { it?.let { delegate.valueFromDB(it) } } as R?

    @Suppress("UNCHECKED_CAST")
    override fun nonNullValueToString(value: R): String =
        value.joinToString(",", "ARRAY[", "]") {
            delegate.nonNullValueToString(it as (T & Any))
        }

    @Suppress("UNCHECKED_CAST")
    override fun nonNullValueAsDefaultString(value: R): String =
        value.joinToString(",", "ARRAY[", "]") {
            delegate.nonNullValueAsDefaultString(it as (T & Any))
        }
}
