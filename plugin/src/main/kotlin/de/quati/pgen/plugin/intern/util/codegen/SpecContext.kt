package de.quati.pgen.plugin.intern.util.codegen

import de.quati.pgen.plugin.intern.model.sql.Column
import de.quati.pgen.plugin.intern.model.sql.SqlObjectName
import de.quati.pgen.plugin.intern.util.codegen.oas.DbContext

internal interface SpecContext {
    val referencesMapping: Map<SqlObjectName, Column.Type>

    context(c: DbContext)
    fun resolve(type: Column.Type): Column.Type.Actual = when (type) {
        is Column.Type.Reference -> getRefTypeOrThrow(type)
        is Column.Type.Actual -> type
    }

    context(c: DbContext)
    private fun getRefTypeOrThrow(ref: Column.Type.Reference): Column.Type.Actual {
        return referencesMapping[ref.name]?.takeIf { it != ref }?.let { type ->
            when (type) {
                is Column.Type.Reference -> getRefTypeOrThrow(type)
                is Column.Type.Actual -> type
            }
        } ?: error("Reference not found: $ref")
    }

    class Base(
        override val referencesMapping: Map<SqlObjectName, Column.Type>,
    ) : SpecContext
}

