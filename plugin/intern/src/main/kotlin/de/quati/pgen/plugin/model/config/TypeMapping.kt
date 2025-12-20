package de.quati.pgen.plugin.model.config

import de.quati.pgen.plugin.model.sql.KotlinValueClass
import de.quati.pgen.plugin.model.sql.SqlObjectName
import kotlinx.serialization.Serializable

@Serializable
data class TypeMapping(
    val sqlType: SqlObjectName,
    val valueClass: KotlinValueClass,
)
