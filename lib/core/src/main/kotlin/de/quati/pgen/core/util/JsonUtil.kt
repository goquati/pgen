package de.quati.pgen.core.util

import de.quati.pgen.core.column.DomainColumnType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomEnumerationColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.StringColumnType
import org.jetbrains.exposed.v1.core.UUIDColumnType
import org.jetbrains.exposed.v1.datetime.KotlinInstantColumnType
import org.jetbrains.exposed.v1.datetime.KotlinLocalDateColumnType
import org.jetbrains.exposed.v1.datetime.KotlinLocalTimeColumnType
import org.jetbrains.exposed.v1.datetime.KotlinOffsetDateTimeColumnType
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant


public fun <T : Any> parseColumnNullable(data: JsonObject, column: Column<T?>): T? {
    val data = data[column.name] ?: throw NoSuchElementException("Column ${column.name} not found in JSON")
    val result = parse(data, column.columnType)
    return result
}

public fun <T : Any> parseColumn(data: JsonObject, column: Column<T>): T {
    val data = data[column.name] ?: throw NoSuchElementException("Column ${column.name} not found in JSON")
    val result = parse(data, column.columnType)
    return result ?: error(
        "Column '${column.name}' is required but was null or invalid"
    )
}

private fun <T : Any> parse(value: JsonElement, type: IColumnType<T>): T? = when (value) {
    JsonNull -> null
    is JsonArray, is JsonObject -> error("Unsupported field value '$value' for column type: ${type::class.simpleName}")
    is JsonPrimitive -> when {
        value.isString -> parseString(value.content, type)
        else -> error("Unsupported field value '$value' for column type: ${type::class.simpleName}")
    }
}


private fun <T : Any> parseString(value: String, type: IColumnType<T>): T = when (type) {
    is StringColumnType -> type.valueFromDB(value)
    is UUIDColumnType -> type.valueFromDB(value)
    is CustomEnumerationColumnType<*> -> type.valueFromDB(value)
    is DomainColumnType<T, *> -> {
        @Suppress("UNCHECKED_CAST")
        val type = type as DomainColumnType<T, Any>
        val inner = parseString(value, type.originType)
        type.builder(inner)
    }

    is KotlinOffsetDateTimeColumnType -> {
        @Suppress("UNCHECKED_CAST")
        OffsetDateTime.parse(value, pgenTimestampTzFormatter) as T
    }

    is KotlinInstantColumnType -> {
        @Suppress("UNCHECKED_CAST")
        @OptIn(ExperimentalTime::class)
        LocalDateTime.parse(value, pgenTimestampFormatter)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toKotlinInstant() as T
    }

    is KotlinLocalDateColumnType -> {
        @Suppress("UNCHECKED_CAST")
        LocalDate.parse(value) as T
    }

    is KotlinLocalTimeColumnType -> {
        @Suppress("UNCHECKED_CAST")
        LocalTime.parse(value) as T
    }

    else -> error("Unsupported column type: ${type::class.simpleName}")
}
