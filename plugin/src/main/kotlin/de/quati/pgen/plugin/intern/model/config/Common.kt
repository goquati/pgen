package de.quati.pgen.plugin.intern.model.config

import de.quati.pgen.plugin.intern.model.spec.KotlinEnumClass
import de.quati.pgen.plugin.intern.model.spec.KotlinValueClass
import de.quati.pgen.plugin.intern.model.spec.SqlColumnName
import de.quati.pgen.plugin.intern.model.spec.SqlObjectName

internal data class TypeMapping(
    val sqlType: SqlObjectName,
    val valueClass: KotlinValueClass,
)

internal data class TypeOverwrite(
    val sqlColumn: SqlColumnName,
    val valueClass: KotlinValueClass,
)

internal data class EnumMapping(
    val sqlType: SqlObjectName,
    val enumClass: KotlinEnumClass,
)