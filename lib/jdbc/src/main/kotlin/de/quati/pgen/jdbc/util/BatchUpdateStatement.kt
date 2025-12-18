package de.quati.pgen.jdbc.util

import de.quati.pgen.intern.BatchUpdateRow
import de.quati.pgen.intern.BatchUpdateStatement
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.UpdateBlockingExecutable
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager


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
) : UpdateBlockingExecutable(statement) {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int {
        return if (this@BatchUpdateExecutable.statement.data.size == 1)
            executeUpdate()
        else
            executeBatch().sum()
    }
}
