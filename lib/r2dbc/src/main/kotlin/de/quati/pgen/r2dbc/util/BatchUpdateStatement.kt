package de.quati.pgen.r2dbc.util

import de.quati.pgen.intern.BatchUpdateRow
import de.quati.pgen.intern.BatchUpdateStatement
import kotlinx.coroutines.flow.reduce
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.statements.UpdateSuspendExecutable
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager


public suspend fun <T : Table, E> T.batchUpdate(
    key: Column<*>,
    data: Collection<E>,
    body: BatchUpdateRowBuilder.(E) -> Unit
): Int = batchUpdate(keys = listOf(key), data = data, body = body)

public suspend fun <T : Table, E> T.batchUpdate(
    keys: List<Column<*>>,
    data: Collection<E>,
    body: BatchUpdateRowBuilder.(E) -> Unit
): Int {
    if (data.isEmpty()) return 0
    val count = BatchUpdateStatement(this, keys).run {
        data.forEach {
            val row = BatchUpdateRowBuilder().apply { body(it) }.build()
            addBatch(row)
        }
        BatchUpdateExecutable(this).execute(TransactionManager.current())
    }
    return count ?: 0
}

public class BatchUpdateRowBuilder {
    private val data = mutableMapOf<Column<*>, Any?>()
    internal fun build() = BatchUpdateRow(data)
    public operator fun <T> set(column: Column<T>, value: T) {
        data[column] = value
    }
}

private class BatchUpdateExecutable(
    override val statement: BatchUpdateStatement
) : UpdateSuspendExecutable(statement) {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction): Int {
        if (this@BatchUpdateExecutable.statement.data.size == 1) executeUpdate() else executeBatch().sum()
        return try {
            this.getResultRow()?.rowsUpdated()?.reduce(Int::plus) ?: 0
        } catch (_: NoSuchElementException) { // flow might be empty
            0
        }
    }
}
