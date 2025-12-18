package de.quati.pgen.jdbc.util

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.UpdateBlockingExecutable
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.util.ArrayList


public fun <T : Table, E> T.batchUpdate(
    key: Column<*>,
    data: Collection<E>,
    body: BatchUpdateRowBuilder.(E) -> Unit
): Int = batchUpdate(keys = listOf(key), data = data, body = body)

public fun <T : Table, E> T.batchUpdate(
    keys: List<Column<*>>,
    data: Collection<E>,
    body: BatchUpdateRowBuilder.(E) -> Unit
): Int {
    if (data.isEmpty()) return 0
    val count = BatchUpdateStatement(this, keys).run {
        data.forEach {
            val batch = BatchUpdateRowBuilder().apply { body(it) }
            addBatch(batch)
        }
        BatchUpdateExecutable(this).execute(TransactionManager.current())
    }
    return count ?: 0
}

public class BatchUpdateRowBuilder {
    internal val data = mutableMapOf<Column<*>, Any?>()
    public operator fun <T> set(column: Column<T>, value: T) {
        data[column] = value
    }
}

private class BatchUpdateExecutable(
    override val statement: BatchUpdateStatement
) : UpdateBlockingExecutable(statement) {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int {
        return if (this@BatchUpdateExecutable.statement.data.size == 1)
            executeUpdate()
        else
            executeBatch().sum()
    }
}

private class BatchUpdateStatement(
    table: Table,
    val keys: List<Column<*>>,
) : UpdateStatement(table, null) {
    val data: ArrayList<Map<Column<*>, Any?>> = ArrayList<Map<Column<*>, Any?>>()
    override val firstDataSet: List<Pair<Column<*>, Any?>> get() =
        data.first().entries.filter { it.key !in keys }.map { it.toPair() }

    fun addBatch(rowBuilder: BatchUpdateRowBuilder) {
        val currentColumns = data.firstOrNull()?.keys
        if (currentColumns == null)
            require(rowBuilder.data.keys.containsAll(keys)) { "Id columns must be present in first row" }
        else
            require(currentColumns == rowBuilder.data.keys) {
                "Columns of current row don't match columns of previous values"
            }
        data.add(rowBuilder.data)
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
