package de.quati.pgen.jdbc.util

import de.quati.kotlin.util.Result
import de.quati.kotlin.util.failure
import de.quati.kotlin.util.success
import de.quati.pgen.core.column.SqlStringHelper
import de.quati.pgen.core.util.DeleteSingleResult
import de.quati.pgen.core.util.UpdateSingleResult
import de.quati.pgen.shared.ConnectionConfig
import de.quati.pgen.shared.ILocalConfigContext
import de.quati.pgen.shared.PgenException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction as jdbcTransaction
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.compoundOr
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.orWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.updateReturning


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

public fun <T> Database.transaction(
    readOnly: Boolean = false,
    block: JdbcTransaction.() -> T,
): T = jdbcTransaction(db = this, readOnly = readOnly) { block() }

public fun ColumnSet.select(builder: MutableList<Expression<*>>.() -> Unit): Query =
    select(buildList(builder))

public fun JdbcTransaction.setLocalConfig(key: String, value: String) {
    val sql = SqlStringHelper.buildSetLocalConfigSql(key = key, value = value)
    exec(sql)
}

public fun JdbcTransaction.setLocalConfig(config: ILocalConfigContext): Unit = setLocalConfig(config.data)
public fun JdbcTransaction.setLocalConfig(config: Map<String, String>) {
    if (config.isEmpty()) return
    val sql = SqlStringHelper.buildSetLocalConfigSql(config)
    exec(sql)
}

context(c: ILocalConfigContext)
public fun <T> Database.transactionWithContext(
    readOnly: Boolean = false,
    statement: JdbcTransaction.() -> T
): T = this.transaction(
    readOnly = readOnly,
) {
    setLocalConfig(c)
    statement()
}


public fun <T> Database.transactionCatching(
    readOnly: Boolean = false,
    statement: JdbcTransaction.() -> T
): Result<T, PgenException> = try {
    this@transactionCatching.transaction(
        readOnly = readOnly,
    ) {
        statement()
    }.success
} catch (t: Throwable) {
    t.toPgenError().failure
}

context(t: JdbcTransaction)
public fun <T : Table> T.deleteSingle(
    op: T.() -> Op<Boolean>
): DeleteSingleResult {
    val sp = t.connection.setSavepoint("pgenDeleteSingle")
    val count = deleteWhere(op = op)
    if (count > 1) t.connection.rollback(sp)
    t.connection.releaseSavepoint(sp)
    return when (count) {
        0 -> DeleteSingleResult.None
        1 -> DeleteSingleResult.Success
        else -> DeleteSingleResult.TooMany
    }
}

context(t: JdbcTransaction)
public fun <T : Table> T.updateSingle(
    returning: List<Expression<*>> = columns,
    where: () -> Op<Boolean>,
    body: T.(UpdateStatement) -> Unit
): UpdateSingleResult {
    val sp = t.connection.setSavepoint("pgenUpdateSingle")
    val rows = updateReturning(
        returning = returning,
        where = where,
        body = body,
    ).take(2).toList()
    if (rows.size > 1) t.connection.rollback(sp)
    t.connection.releaseSavepoint(sp)
    return when (rows.size) {
        0 -> UpdateSingleResult.None
        1 -> UpdateSingleResult.Success(rows.single())
        else -> UpdateSingleResult.TooMany
    }
}

public fun Query.orWhere(con: Op<Boolean>?, vararg cons: Op<Boolean>?): Query = orWhere {
    (listOfNotNull(con) + cons).filterNotNull().let {
        when (it.size) {
            0 -> Op.TRUE
            1 -> it.single()
            else -> it.compoundOr()
        }
    }
}

public fun Query.andWhere(con: Op<Boolean>?, vararg cons: Op<Boolean>?): Query = andWhere {
    (listOfNotNull(con) + cons).filterNotNull().let {
        when (it.size) {
            0 -> Op.TRUE
            1 -> it.single()
            else -> it.compoundAnd()
        }
    }
}
