package de.quati.pgen.jdbc.column

import de.quati.pgen.core.column.parseFloatArray
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.postgresql.util.PGobject

public class PgVectorColumnType(
    private val schema: String,
) : ColumnType<FloatArray>() {
    override fun sqlType(): String = "${schema}.vector"
    override fun notNullValueToDB(value: FloatArray): FloatArray = value
    override fun valueFromDB(value: Any): FloatArray = parseFloatArrayFormDb(value)
}

public fun Table.pgVector(
    name: String,
    schema: String,
): Column<FloatArray> {
    val type = PgVectorColumnType(schema = schema)
    return registerColumn(name = name, type = type)
}

internal fun parseFloatArrayFormDb(value: Any): FloatArray {
    return when (value) {
        is PGobject -> value.value?.let(::parseFloatArray) ?: error("Cannot convert null to FloatArray")
        is String -> value.let(::parseFloatArray)
        else -> error("Cannot convert $value to FloatArray")
    }
}