package de.quati.pgen.plugin.intern.model.config

import de.quati.pgen.plugin.intern.model.sql.KotlinEnumClass
import de.quati.pgen.plugin.intern.model.sql.SqlObjectName
import kotlinx.serialization.Serializable

@Serializable
data class EnumMapping(
    val sqlType: SqlObjectName,
    val enumClass: KotlinEnumClass,
)
