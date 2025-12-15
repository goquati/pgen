package de.quati.pgen.core.column

import org.jetbrains.exposed.v1.core.ColumnType

public abstract class CompositeColumnType<T : Any> : ColumnType<T>()