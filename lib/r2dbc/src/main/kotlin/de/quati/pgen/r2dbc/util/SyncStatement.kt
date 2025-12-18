package de.quati.pgen.r2dbc.util

import de.quati.pgen.intern.SyncKeysBuilder
import de.quati.pgen.intern.SyncRow
import de.quati.pgen.intern.SyncStatement
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.buildStatement
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.statements.SuspendExecutable
import org.jetbrains.exposed.v1.r2dbc.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager


public suspend fun <T : Table, K, V> T.sync(
    key: Pair<Column<K>, K>,
    data: Collection<V>,
    block: SyncRowBuilder.(V) -> Unit,
): Unit = sync(
    keys = mapOf(key.first to QueryParameter(key.second, key.first.columnType)),
    data = data,
    block = block,
)

public suspend fun <T : Table, V> T.sync(
    keys: SyncKeysBuilder.() -> Unit,
    data: Collection<V>,
    block: SyncRowBuilder.(V) -> Unit,
): Unit = sync(
    keys = SyncKeysBuilder().apply(keys).keys,
    data = data,
    block = block,
)

public class SyncRowBuilder {
    private val columns = mutableListOf<Column<*>>()
    private val values = mutableListOf<Expression<*>>()
    internal fun build() = SyncRow(columns = columns, values = values)

    public operator fun <T> set(column: Column<T>, value: T) {
        columns.add(column)
        values.add(QueryParameter(value, column.columnType))
    }
}

private class SyncSuspendExecutable(
    override val statement: SyncStatement
) : SuspendExecutable<Unit, SyncStatement> {
    override suspend fun R2dbcPreparedStatementApi.executeInternal(transaction: R2dbcTransaction) {
        executeUpdate()
        getResultRow()!!.collect()
    }
}

private suspend fun <T : Table, V> T.sync(
    keys: Map<Column<*>, QueryParameter<*>>,
    data: Collection<V>,
    block: SyncRowBuilder.(V) -> Unit,
) {
    if (data.isEmpty()) {
        deleteWhere { keys.map { it.key eq it.value }.compoundAnd() }
        return
    }
    val stmt = buildStatement {
        SyncStatement(
            targetsSet = this@sync,
            keys = keys,
        ).apply {
            data.forEach {
                val row = SyncRowBuilder().apply { block(it) }.build()
                addRow(row)
            }
        }
    }
    SyncSuspendExecutable(stmt).execute(TransactionManager.current())
}
