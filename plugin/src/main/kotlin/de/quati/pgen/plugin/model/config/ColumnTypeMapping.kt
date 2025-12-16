package de.quati.pgen.plugin.model.config

import de.quati.pgen.plugin.model.sql.Column.Type.CustomPrimitive
import de.quati.pgen.plugin.model.sql.KotlinClassName
import de.quati.pgen.plugin.model.sql.SqlObjectName
import kotlinx.serialization.Serializable

@Serializable
data class ColumnTypeMapping(
    val sqlType: SqlObjectName,
    val columnType: KotlinClassName,
    val value: KotlinClassName,
) {
    fun toCustomPrimitiveType() = CustomPrimitive(
        sqlType = sqlType.schema.schemaName + "." + sqlType.name,
    )
}
