package de.quati.pgen.intern

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import java.util.ArrayList

public class BatchUpdateRow(
    public val data: Map<Column<*>, Any?>,
)

public class BatchUpdateStatement(
    table: Table,
    public val keys: List<Column<*>>,
) : UpdateStatement(table, null) {
    public val data: ArrayList<Map<Column<*>, Any?>> = ArrayList<Map<Column<*>, Any?>>()
    override val firstDataSet: List<Pair<Column<*>, Any?>>
        get() =
            data.first().entries.filter { it.key !in keys }.map { it.toPair() }

    public fun addBatch(row: BatchUpdateRow) {
        val currentColumns = data.firstOrNull()?.keys
        if (currentColumns == null)
            require(row.data.keys.containsAll(keys)) { "Id columns must be present in first row" }
        else
            require(currentColumns == row.data.keys) {
                "Columns of current row don't match columns of previous values"
            }
        data.add(row.data)
    }

    override fun <T, S : T?> update(column: Column<T>, value: Expression<S>): Nothing =
        error("Expressions unsupported in batch update")

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        val updateSql = super.prepareSQL(transaction, prepared)
        val idEqCondition = keys.joinToString(separator = " AND ") { "${transaction.identity(it)} = ?" }
        return "$updateSql WHERE $idEqCondition"
    }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> {
        val valueColumns = firstDataSet.map { it.first }
        return data.map { row ->
            val idArgs = keys.map { it.columnType to row[it] }
            valueColumns.map { it.columnType to row[it] } + idArgs
        }
    }
}
