package de.quati.pgen.plugin.intern.service.db

import de.quati.pgen.plugin.intern.model.spec.Column
import de.quati.pgen.plugin.intern.model.spec.Column.Type
import de.quati.pgen.plugin.intern.model.spec.SchemaName
import de.quati.pgen.plugin.intern.model.spec.SqlObjectName
import de.quati.pgen.plugin.intern.service.db.TypeData.Companion.parseTypeData
import java.sql.ResultSet


internal data class ColumnData(
    val pos: Int,
    val tableSchema: SchemaName,
    val tableName: String,
    val columnName: String,
    val isNullable: Boolean,
    val columnDefault: String?,
    val typeData: TypeData,
) {
    companion object {
        fun ResultSet.parseColumnData(): ColumnData {
            return ColumnData(
                pos = getInt("pos"),
                tableSchema = getString("table_schema")!!.let(::SchemaName),
                tableName = getString("table_name"),
                columnName = getString("column_name"),
                isNullable = getBoolean("is_nullable"),
                columnDefault = getString("column_default"),
                typeData = parseTypeData(),
            )
        }
    }
}

internal data class TypeData(
    val domainSchema: SchemaName?,
    val domainName: String?,
    val innerTypeSchema: SchemaName,
    val innerTypeName: String,
    val numericPrecision: Int?,
    val numericScale: Int?,
    val typeCategory: String?,
    val elementTypeCategory: String?,
) {
    companion object {
        fun ResultSet.parseTypeData(): TypeData {
            return TypeData(
                domainSchema = getString("domain_schema")?.let(::SchemaName),
                domainName = getString("domain_name"),
                innerTypeSchema = getString("inner_type_schema")!!.let(::SchemaName),
                innerTypeName = getString("inner_type_name"),
                numericPrecision = getInt("numeric_precision").takeIf { !wasNull() },
                numericScale = getInt("numeric_scale").takeIf { !wasNull() },
                typeCategory = getString("type_category"),
                elementTypeCategory = getString("element_type_category"),
            )
        }
    }
}