package de.quati.pgen.r2dbc.column

import de.quati.pgen.core.column.CompositeColumnType
import de.quati.pgen.core.column.PgEnum
import de.quati.pgen.core.column.PgenColumnType
import de.quati.pgen.core.model.PgenMultiRange
import de.quati.pgen.core.model.PgenRange
import io.r2dbc.postgresql.codec.PostgresTypes
import io.r2dbc.postgresql.codec.PostgresqlObjectId
import io.r2dbc.spi.Parameters
import io.r2dbc.spi.Statement
import io.r2dbc.spi.Type
import org.jetbrains.exposed.v1.core.ArrayColumnType
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
        IntervalColumnType::class,
        CompositeColumnType::class,
        PgenColumnType::class,
        ArrayColumnType::class,
    )

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        typeMapping: R2dbcTypeMapping,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int,
    ): Boolean {
        return if (value == null) false
        else when (columnType) {
            is CitextColumnType -> {
                statement.bind(
                    index - 1,
                    Parameters.`in`(PostgresqlObjectId.UNSPECIFIED, value),
                )
                true
            }

            is Int4RangeColumnType -> {
                statement.bind(
                    index - 1,
                    Parameters.`in`(
                        PG_INT4RANGE_TYPE, PgenRange.toPostgresqlValue(value as IntRange)
                    )
                )
                true
            }

            is Int8RangeColumnType -> {
                statement.bind(
                    index - 1,
                    Parameters.`in`(
                        PG_INT8RANGE_TYPE, PgenRange.toPostgresqlValue(value as LongRange)
                    )
                )
                true
            }

            is Int4MultiRangeColumnType -> {
                statement.bind(
                    index - 1,
                    Parameters.`in`(
                        PG_INT4MULTIRANGE_TYPE, (value as PgenMultiRange<*>).toPostgresqlValue()
                    )
                )
                true
            }

            is Int8MultiRangeColumnType -> {
                statement.bind(
                    index - 1,
                    Parameters.`in`(
                        PG_INT8MULTIRANGE_TYPE, (value as PgenMultiRange<*>).toPostgresqlValue()
                    )
                )
                true
            }

            is IntervalColumnType -> {
                statement.bind(index - 1, Parameters.`in`(PG_INTERVAL_TYPE, value))
                true
            }

            is CompositeColumnType<*> -> {
                statement.bind(index - 1, Parameters.`in`(PostgresqlObjectId.UNSPECIFIED, value))
                true
            }

            is PgenColumnType -> {
                statement.bind(index - 1, Parameters.`in`(columnType.typeInfo.toPostgresType(), value))
                true
            }

            is ArrayColumnType<*, *> -> { // support pgen enum arrays
                if (columnType.dimensions != 1 || columnType.delegate !is CustomEnumerationColumnType) return false
                if ((value as? Array<*>)?.any { it !is PgEnum } ?: return false) return false
                val value = value.joinToString(",", "{", "}") { it.toString() }
                statement.bind(index - 1, Parameters.`in`(PostgresqlObjectId.UNSPECIFIED, value))
                true
            }

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

        private val PG_INT4RANGE_TYPE = createType(3904, typarray = 3905, name = "int4range", category = "R")
        private val PG_INT8RANGE_TYPE = createType(3926, typarray = 3927, name = "int8range", category = "R")
        private val PG_INT4MULTIRANGE_TYPE = createType(4451, typarray = 6150, name = "int4multirange", category = "R")
        private val PG_INT8MULTIRANGE_TYPE = createType(4536, typarray = 6157, name = "int8multirange", category = "R")
        private val PG_INTERVAL_TYPE = createType(1186, typarray = 1187, name = "interval", category = "R")
    }
}
