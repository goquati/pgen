package de.quati.pgen.core.column

import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ArrayColumnType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.CustomEnumerationColumnType
import org.jetbrains.exposed.v1.core.Table

public fun <E> getArrayColumnType(columnType: ColumnType<E & Any>): ArrayColumnType<E, List<E>> =
    ArrayColumnType<E, List<E>>(delegate = columnType)

public fun parseFloatArray(data: String): FloatArray {
    val cleaned = data.trim().removePrefix("[").removeSuffix("]")
    return cleaned.split(",").map { it.trim().toFloat() }.toFloatArray()
}

public object SqlStringHelper : TextColumnType() {
    public fun buildSetLocalConfigSql(key: String, value: String): String =
        "set local ${escapeAndQuote(key)} = ${nonNullValueToString(value)};"

    public fun buildSetLocalConfigSql(config: Map<String, String>): String =
        config.entries.joinToString(separator = "\n") { (k, v) -> buildSetLocalConfigSql(k, v) }
}
