package de.quati.pgen.core

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

public class ColumnValue<T>(
    public val column: Column<T>,
    public val value: T,
)

public interface ColumnValueSet {
    public fun toList(): List<ColumnValue<*>>
    public infix fun applyTo(builder: UpdateBuilder<*>): Unit = toList().forEach { builder.set(it) }

    public companion object Companion {
        public fun <T> UpdateBuilder<*>.set(data: ColumnValue<T>): Unit = set(data.column, data.value)
        public fun UpdateBuilder<*>.set(data: ColumnValueSet): Unit = data.toList().forEach { set(it) }
    }
}