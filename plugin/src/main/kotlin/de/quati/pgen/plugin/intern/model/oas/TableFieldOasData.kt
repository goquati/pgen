package de.quati.pgen.plugin.intern.model.oas

import de.quati.pgen.plugin.intern.model.sql.Column

data class TableFieldOasData(
    val name: String,
    val nullable: Boolean,
    val ignoreAtCreate: Boolean,
    val ignoreAtUpdate: Boolean,
    val type: TableFieldTypeOasData,
    val sqlData: Column,
)