package de.quati.pgen.plugin.intern.model.config

import de.quati.pgen.plugin.intern.model.sql.Column.Type.CustomPrimitive
import de.quati.pgen.plugin.intern.model.sql.KotlinClassName
import de.quati.pgen.plugin.intern.model.sql.SqlObjectName
import kotlinx.serialization.Serializable

@Serializable
internal data class ColumnTypeMapping(
    val sqlType: SqlObjectName,
    val columnType: KotlinClassName,
    val value: KotlinClassName,
) {
    fun toCustomPrimitiveType() = CustomPrimitive(
        sqlType = sqlType.schema.schemaName + "." + sqlType.name,
    )
}
