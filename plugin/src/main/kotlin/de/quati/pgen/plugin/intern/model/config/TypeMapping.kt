package de.quati.pgen.plugin.intern.model.config

import de.quati.pgen.plugin.intern.model.sql.KotlinValueClass
import de.quati.pgen.plugin.intern.model.sql.SqlObjectName
import kotlinx.serialization.Serializable

@Serializable
internal data class TypeMapping(
    val sqlType: SqlObjectName,
    val valueClass: KotlinValueClass,
)
