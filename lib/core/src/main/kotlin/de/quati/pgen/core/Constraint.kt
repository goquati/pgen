package de.quati.pgen.core

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table


public sealed interface Constraint {
    public val table: Table
    public val name: String

    public sealed interface IUnique : Constraint

    public data class PrimaryKey(override val table: Table, override val name: String) : IUnique
    public data class ForeignKey(override val table: Table, override val name: String) : Constraint
    public data class Unique(override val table: Table, override val name: String) : IUnique
    public data class Check(override val table: Table, override val name: String) : Constraint
    public data class NotNull(val column: Column<out Any>, override val name: String) : Constraint {
        override val table: Table = column.table
    }
}