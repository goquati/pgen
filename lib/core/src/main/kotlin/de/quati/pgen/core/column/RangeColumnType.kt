package de.quati.pgen.core.column

import de.quati.pgen.core.util.PgenRange
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi

public abstract class RangeColumnType<T : Comparable<T>, R : ClosedRange<T>>(
    private val sqlType: String,
    private val fromDb: (Any) -> R?,
    private val toDb: (R) -> Any?,
) : ColumnType<R>() {
    override fun sqlType(): String = sqlType
    override fun nonNullValueToString(value: R): String = PgenRange.toPostgresqlValue(value)
    override fun nonNullValueAsDefaultString(value: R): String = "'${nonNullValueToString(value)}'"

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val parameterValue = value?.let {
            @Suppress("UNCHECKED_CAST")
            it as R
        }?.let {
            toDb(it)
        }
        super.setParameter(stmt, index, parameterValue)
    }

    override fun valueFromDB(value: Any): R? = fromDb(value)
}