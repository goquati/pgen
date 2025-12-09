package de.quati.pgen.shared

public data class PgenErrorDetails(
    val code: String,
    val columnName: String?,
    val constraintName: String?,
    val dataTypeName: String?,
    val detail: String?,
    val file: String?,
    val hint: String?,
    val internalPosition: String?,
    val internalQuery: String?,
    val line: String?,
    val message: String,
    val position: String?,
    val routine: String?,
    val schemaName: String?,
    val severityLocalized: String,
    val severityNonLocalized: String,
    val tableName: String?,
    val where: String?,
)