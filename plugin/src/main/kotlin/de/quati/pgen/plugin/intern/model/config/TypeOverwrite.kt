package de.quati.pgen.plugin.intern.model.config

import de.quati.pgen.plugin.intern.model.sql.KotlinValueClass
import de.quati.pgen.plugin.intern.model.sql.SqlColumnName
import kotlinx.serialization.Serializable

@Serializable
data class TypeOverwrite(
    val sqlColumn: SqlColumnName,
    val valueClass: KotlinValueClass,
)