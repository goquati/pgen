package de.quati.pgen.plugin.model.config

import de.quati.pgen.plugin.model.sql.KotlinValueClass
import de.quati.pgen.plugin.model.sql.SqlColumnName
import kotlinx.serialization.Serializable

@Serializable
data class TypeOverwrite(
    val sqlColumn: SqlColumnName,
    val valueClass: KotlinValueClass,
)