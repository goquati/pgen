package de.quati.pgen.r2dbc.column

import de.quati.pgen.core.column.CompositeColumnType
import de.quati.pgen.shared.PgenEnum
import de.quati.pgen.core.column.PgenColumnType
import io.r2dbc.postgresql.codec.PostgresTypes
import io.r2dbc.postgresql.codec.PostgresqlObjectId
import io.r2dbc.spi.Parameters
import io.r2dbc.spi.Statement
import io.r2dbc.spi.Type
import org.jetbrains.exposed.v1.core.CustomEnumerationColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.mappers.R2dbcTypeMapping
import org.jetbrains.exposed.v1.r2dbc.mappers.TypeMapper
import kotlin.reflect.KClass


public class PgenTypeMapper : TypeMapper {
    override val priority: Double = 1.9
    override val dialects: List<KClass<PostgreSQLDialect>> = listOf(PostgreSQLDialect::class)
    override val columnTypes: List<KClass<out IColumnType<*>>> = listOf(
        CitextColumnType::class,
        Int4RangeColumnType::class,
        Int8RangeColumnType::class,
        Int4MultiRangeColumnType::class,
        Int8MultiRangeColumnType::class,
        IntervalColumnType::class,
        CompositeColumnType::class,
        PgenColumnType::class,
        PgenEnumArrayColumnType::class,
        CustomEnumerationColumnType::class,
    )

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        typeMapping: R2dbcTypeMapping,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int,
    ): Boolean {
        fun bind(type: Type, value: Any): Boolean {
            statement.bind(
                index - 1,
                Parameters.`in`(type, value),
            )
            return true
        }

        return if (value == null) false
        else when (columnType) {
            is CitextColumnType -> bind(PostgresqlObjectId.UNSPECIFIED, value)
            is IntervalColumnType -> bind(PostgresqlObjectId.INTERVAL, value)
            is CompositeColumnType<*> -> bind(PostgresqlObjectId.UNSPECIFIED, value)
            is PgenColumnType -> bind(columnType.typeInfo.toPostgresType(), value)
            is Int4RangeColumnType -> bind(INT4RANGE_TYPE, value)
            is Int8RangeColumnType -> bind(INT8RANGE_TYPE, value)
            is Int4MultiRangeColumnType -> bind(INT4MULTIRANGE_TYPE, value)
            is Int8MultiRangeColumnType -> bind(INT8MULTIRANGE_TYPE, value)
            is PgenEnumArrayColumnType<*, *> -> bind(PostgresqlObjectId.UNSPECIFIED, value)

            is CustomEnumerationColumnType -> if (value !is PgenEnum)
                false // only pgen enums, skip other enum types
            else
                bind(PostgresqlObjectId.UNSPECIFIED, value.pgenEnumLabel)

            else -> false
        }
    }

    private companion object {
        private fun PgenColumnType.TypeInfo?.toPostgresType(): Type = if (this == null)
            PostgresqlObjectId.UNSPECIFIED
        else
            PostgresTypes.PostgresType(oid, unsignedOid, typarray, unsignedTyparrayval, name, category)

        private fun createType(oid: Int, typarray: Int, name: String, category: String): PostgresTypes.PostgresType =
            PostgresTypes.PostgresType(oid, oid.toLong(), typarray, typarray.toLong(), name, category)

        private val INT4RANGE_TYPE = createType(3904, typarray = 3905, name = "int4range", category = "R")
        private val INT8RANGE_TYPE = createType(3926, typarray = 3927, name = "int8range", category = "R")
        private val INT4MULTIRANGE_TYPE = createType(4451, typarray = 6150, name = "int4multirange", category = "R")
        private val INT8MULTIRANGE_TYPE = createType(4536, typarray = 6157, name = "int8multirange", category = "R")
    }
}
