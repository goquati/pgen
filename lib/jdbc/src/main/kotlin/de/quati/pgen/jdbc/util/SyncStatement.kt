package de.quati.pgen.jdbc.util

import de.quati.pgen.core.util.SyncKeysBuilder
import de.quati.pgen.core.util.SyncRowBuilder
import de.quati.pgen.core.util.SyncStatement
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.buildStatement
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.statements.BlockingExecutable
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager


public fun <T : Table, K, V> T.sync(
    key: Pair<Column<K>, K>,
    data: Collection<V>,
    block: SyncRowBuilder.(V) -> Unit,
): Unit = sync(
    keys = mapOf(key.first to QueryParameter(key.second, key.first.columnType)),
    data = data,
    block = block,
)

public fun <T : Table, V> T.sync(
    keys: SyncKeysBuilder.() -> Unit,
    data: Collection<V>,
    block: SyncRowBuilder.(V) -> Unit,
): Unit = sync(
    keys = SyncKeysBuilder().apply(keys).keys,
    data = data,
    block = block,
)

private class SyncSuspendExecutable(
    override val statement: SyncStatement
) : BlockingExecutable<Unit, SyncStatement> {
    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction) {
        executeUpdate()
    }
}

private fun <T : Table, V> T.sync(
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
                val row = SyncRowBuilder().apply { block(it) }
                addRow(row)
            }
        }
    }
    SyncSuspendExecutable(stmt).execute(TransactionManager.current())
}
