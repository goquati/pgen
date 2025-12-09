package de.quati.pgen.r2dbc.util

import de.quati.pgen.core.column.SqlStringHelper
import de.quati.pgen.core.util.DeleteSingleResult
import de.quati.pgen.core.util.UpdateSingleResult
import de.quati.pgen.shared.ILocalConfigContext
import de.quati.pgen.shared.PgenException
import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.r2dbc.Query
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction as r2dbcSuspendTransaction
import kotlin.coroutines.CoroutineContext
import org.jetbrains.exposed.v1.r2dbc.transactions.transactionManager
import de.quati.kotlin.util.Result
import de.quati.kotlin.util.failure
import de.quati.kotlin.util.success
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.supervisorScope
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.updateReturning

public fun ColumnSet.select(builder: MutableList<Expression<*>>.() -> Unit): Query =
    select(buildList(builder))

public suspend fun <T> R2dbcDatabase.suspendTransaction(
    transactionIsolation: IsolationLevel? = null,
    readOnly: Boolean = false,
    statement: suspend R2dbcTransaction.() -> T
): T {
    val isolation = transactionIsolation ?: transactionManager.defaultIsolationLevel
    require(isolation != null) { "A default isolation level for this transaction has not been set" }
    val result = r2dbcSuspendTransaction(
        transactionIsolation = isolation,
        readOnly = readOnly,
        db = this,
        statement = statement,
    )
    return result
}

public fun <T> R2dbcDatabase.blockingTransaction(
    context: CoroutineContext = Dispatchers.IO,
    readOnly: Boolean = false,
    block: suspend R2dbcTransaction.() -> T,
): T = runBlocking(context) {
    val result = suspendTransaction(readOnly = readOnly, statement = block)
    result
}

public fun interface TransactionFlowScope<T> {
    context(_: Transaction, _: ProducerScope<T>)
    public suspend fun block()
}

public fun <T> R2dbcDatabase.transactionFlow(
    readOnly: Boolean = false,
    block: TransactionFlowScope<T>,
): Flow<T> {
    return channelFlow {
        this@transactionFlow.suspendTransaction(
            readOnly = readOnly,
        ) {
            block.block()
        }
    }
}

public suspend fun R2dbcTransaction.setLocalConfig(key: String, value: String) {
    val sql = SqlStringHelper.buildSetLocalConfigSql(key = key, value = value)
    exec(sql)
}

public suspend fun R2dbcTransaction.setLocalConfig(config: ILocalConfigContext): Unit = setLocalConfig(config.data)
public suspend fun R2dbcTransaction.setLocalConfig(config: Map<String, String>) {
    if (config.isEmpty()) return
    val sql = SqlStringHelper.buildSetLocalConfigSql(config)
    exec(sql)
}

context(c: ILocalConfigContext)
public suspend fun <T> R2dbcDatabase.suspendTransactionWithContext(
    transactionIsolation: IsolationLevel? = null,
    readOnly: Boolean = false,
    statement: suspend R2dbcTransaction.() -> T
): T = this.suspendTransaction(
    transactionIsolation = transactionIsolation,
    readOnly = readOnly,
) {
    setLocalConfig(c)
    statement()
}

context(c: ILocalConfigContext)
public fun <T> R2dbcDatabase.blockingTransactionWithContext(
    context: CoroutineContext = Dispatchers.IO,
    readOnly: Boolean = false,
    block: suspend R2dbcTransaction.() -> T,
): T = this.blockingTransaction(
    context = context,
    readOnly = readOnly,
) {
    setLocalConfig(c)
    block()
}

context(c: ILocalConfigContext)
public fun <T> R2dbcDatabase.transactionFlowWithContext(
    readOnly: Boolean = false,
    block: TransactionFlowScope<T>,
): Flow<T> {
    return channelFlow {
        this@transactionFlowWithContext.suspendTransaction(
            readOnly = readOnly,
        ) {
            setLocalConfig(c)
            block.block()
        }
    }
}

public suspend fun <T> R2dbcDatabase.suspendTransactionCatching(
    transactionIsolation: IsolationLevel? = null,
    readOnly: Boolean = false,
    statement: suspend R2dbcTransaction.() -> T
): Result<T, PgenException> = supervisorScope {
    try {
        this@suspendTransactionCatching.suspendTransaction(
            transactionIsolation = transactionIsolation,
            readOnly = readOnly,
        ) {
            statement()
        }.success
    } catch (t: Throwable) {
        t.toPgenError().failure
    }
}

context(t: R2dbcTransaction)
public suspend fun <T : Table> T.deleteSingle(
    op: T.() -> Op<Boolean>
): DeleteSingleResult {
    val sp = t.connection().setSavepoint("pgenDeleteSingle")
    val count = deleteWhere(op = op)
    if (count > 1) t.connection().rollback(sp)
    t.connection().releaseSavepoint(sp)
    return when (count) {
        0 -> DeleteSingleResult.None
        1 -> DeleteSingleResult.Success
        else -> DeleteSingleResult.TooMany
    }
}

context(t: R2dbcTransaction)
public suspend fun <T : Table> T.updateSingle(
    returning: List<Expression<*>> = columns,
    where: () -> Op<Boolean>,
    body: T.(UpdateStatement) -> Unit
): UpdateSingleResult {
    val sp = t.connection().setSavepoint("pgenUpdateSingle")
    val rows = updateReturning(
        returning = returning,
        where = where,
        body = body,
    ).take(2).toList()
    if (rows.size > 1) t.connection().rollback(sp)
    t.connection().releaseSavepoint(sp)
    return when (rows.size) {
        0 -> UpdateSingleResult.None
        1 -> UpdateSingleResult.Success(rows.single())
        else -> UpdateSingleResult.TooMany
    }
}

public fun Query.andWhereIfNotNull(andPart: () -> Op<Boolean>?): Query {
    val expr = andPart() ?: return this@andWhereIfNotNull
    return adjustWhere { if (this == null) expr else this and expr }
}

public fun Query.orWhereIfNotNull(andPart: () -> Op<Boolean>?): Query {
    val expr = andPart() ?: return this@orWhereIfNotNull
    return adjustWhere { if (this == null) expr else this or expr }
}
