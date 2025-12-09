package de.quati.pgen.jdbc.util

import de.quati.pgen.shared.ConnectionConfig
import kotlinx.coroutines.channels.ProducerScope
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction as jdbcTransaction
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select


public fun Database.Companion.connect(
    connectionConfig: ConnectionConfig.Jdbc,
    user: String,
    password: String,
): Database {
    val url = connectionConfig.url()
    val db = Database.connect(
        url = url,
        user = user,
        password = password
    )
    return db
}

public fun <T> Database.transaction(block: Transaction.() -> T): T =
    jdbcTransaction(db = this) { block() }

public fun interface TransactionFlowScope<T> {
    context(_: Transaction, _: ProducerScope<T>)
    public suspend fun block()
}

public fun ColumnSet.select(builder: MutableList<Expression<*>>.() -> Unit): Query =
    select(buildList(builder))
