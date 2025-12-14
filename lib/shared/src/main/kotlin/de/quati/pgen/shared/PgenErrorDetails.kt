package de.quati.pgen.shared

public data class PgenErrorDetails(
    val code: String,
    val columnName: String?,
    val constraintName: String?,
    val dataTypeName: String?,
    val detail: String?,
    val file: String?,
    val hint: String?,
    val internalPosition: Int?,
    val internalQuery: String?,
    val line: Int?,
    val message: String,
    val position: Int?,
    val routine: String?,
    val schemaName: String?,
    val severityLocalized: String?,
    val severityNonLocalized: String?,
    val tableName: String?,
    val where: String?,
)