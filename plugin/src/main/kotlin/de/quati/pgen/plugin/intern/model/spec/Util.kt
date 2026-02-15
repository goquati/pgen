package de.quati.pgen.plugin.intern.model.spec

import de.quati.pgen.plugin.intern.codegen.CodeGenContext

context(c: CodeGenContext)
internal fun Column.Type.isSupportedForWalEvents(): Boolean = when (val type = c.resolve(this)) {
    is Column.Type.CustomType,
    is Column.Type.Overwrite,
    is Column.Type.NonPrimitive.Array,
    is Column.Type.NonPrimitive.Composite,
    is Column.Type.NonPrimitive.Numeric,
    is Column.Type.NonPrimitive.PgVector,
    Column.Type.Primitive.BINARY,
    Column.Type.Primitive.INT4RANGE,
    Column.Type.Primitive.INT8RANGE,
    Column.Type.Primitive.INT4MULTIRANGE,
    Column.Type.Primitive.INT8MULTIRANGE,
    Column.Type.Primitive.INTERVAL,
    Column.Type.Primitive.JSON,
    Column.Type.Primitive.JSONB,
    Column.Type.Primitive.UNCONSTRAINED_NUMERIC,
    Column.Type.Primitive.REG_CLASS,
        -> false

    Column.Type.Primitive.BOOL,
    Column.Type.Primitive.INT2,
    Column.Type.Primitive.INT4,
    Column.Type.Primitive.INT8,
    Column.Type.Primitive.FLOAT4,
    Column.Type.Primitive.FLOAT8,
    Column.Type.Primitive.TEXT,
    Column.Type.Primitive.UUID,
    Column.Type.Primitive.VARCHAR,
    Column.Type.Primitive.CITEXT,
    Column.Type.Primitive.DATE,
    Column.Type.Primitive.TIME,
    Column.Type.Primitive.TIMESTAMP,
    Column.Type.Primitive.TIMESTAMP_WITH_TIMEZONE,
    is Column.Type.NonPrimitive.Enum,
        -> true

    is Column.Type.NonPrimitive.Domain -> type.base.isSupportedForWalEvents()
}