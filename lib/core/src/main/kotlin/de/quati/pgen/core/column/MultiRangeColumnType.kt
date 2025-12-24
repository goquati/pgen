package de.quati.pgen.core.column

import de.quati.pgen.core.util.toPostgresqlValue
import de.quati.pgen.shared.PgenMultiRange
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi

public abstract class MultiRangeColumnType<T : Comparable<T>>(
    private val sqlType: String,
    private val fromDb: (Any) -> PgenMultiRange<T>?,
    private val toDb: (PgenMultiRange<T>) -> Any?,
) : ColumnType<PgenMultiRange<T>>() {
    override fun sqlType(): String = sqlType
    override fun nonNullValueToString(value: PgenMultiRange<T>): String = value.toPostgresqlValue()

    override fun nonNullValueAsDefaultString(value: PgenMultiRange<T>): String =
        "'${nonNullValueToString(value)}'"

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val parameterValue = value?.let {
            @Suppress("UNCHECKED_CAST")
            it as PgenMultiRange<T>
        }?.let {
            toDb(it)
        }
        super.setParameter(stmt, index, parameterValue)
    }

    override fun valueFromDB(value: Any): PgenMultiRange<T>? = fromDb(value)
}
