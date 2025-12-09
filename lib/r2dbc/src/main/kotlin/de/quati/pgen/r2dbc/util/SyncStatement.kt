package de.quati.pgen.r2dbc.util

import org.jetbrains.exposed.v1.core.AbstractQuery
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.QueryAlias
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.Slice
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.MergeStatement
import org.jetbrains.exposed.v1.core.statements.Statement
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.core.statements.buildStatement
import org.jetbrains.exposed.v1.core.targetTables
import org.jetbrains.exposed.v1.core.vendors.FunctionProvider
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
    internal val columns = mutableListOf<Column<*>>()
    internal val values = mutableListOf<Expression<*>>()

    public operator fun <T> set(column: Column<T>, value: T) {
        columns.add(column)
        values.add(QueryParameter(value, column.columnType))
    }
}

public class SyncKeysBuilder {
    internal val keys = mutableMapOf<Column<*>, QueryParameter<*>>()

    public operator fun <T> set(column: Column<T>, value: T) {
        keys[column] = QueryParameter(value, column.columnType)
    }
}

private abstract class SyncBuilder<out T>(targets: List<Table>) : Statement<T>(StatementType.MERGE, targets) {
    var builderValuesColumns: List<Column<*>>? = null
    val builderValuesRows = mutableListOf<List<Expression<*>>>()

    fun addRow(rowBuilder: SyncRowBuilder) {
        if (builderValuesColumns == null)
            builderValuesColumns = rowBuilder.columns
        else
            require(builderValuesColumns == rowBuilder.columns) {
                "Columns of current row don't match columns of previous values"
            }
        builderValuesRows.add(rowBuilder.values)
    }
}

private class SyncStatement(
    val targetsSet: Table,
    val keys: Map<Column<*>, QueryParameter<*>>,
) : SyncBuilder<Unit>(targetsSet.targetTables()) {
    val valuesColumns: List<Column<*>> get() = builderValuesColumns ?: emptyList()
    val valuesRows: List<List<Expression<*>>> get() = builderValuesRows


    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String {
        require(valuesRows.isNotEmpty()) { "Cannot prepare SQL for empty values" }
        return transaction.db.dialect.functionProvider.sync(
            dest = targetsSet,
            transaction = transaction,
            keys = keys,
            valuesColumns = valuesColumns,
            valuesRows = valuesRows,
        )
    }

    override fun arguments(): Iterable<Iterable<Pair<IColumnType<*>, Any?>>> = QueryBuilder(true).run {
        registerValueArgs()
        if (args.isNotEmpty()) listOf(args) else emptyList()
    }

    private fun QueryBuilder.registerValueArgs() {
        valuesRows.forEach { row ->
            require(row.size == valuesColumns.size)
            valuesColumns.zip(row).forEach { (c, v) ->
                registerArgument(c, v)
            }
        }
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
                val row = SyncRowBuilder().apply { block(it) }
                addRow(row)
            }
        }
    }
    SyncSuspendExecutable(stmt).execute(TransactionManager.current())
}

private fun FunctionProvider.sync(
    dest: Table,
    transaction: Transaction,
    keys: Map<Column<*>, Expression<*>>,
    valuesColumns: List<Column<*>>,
    valuesRows: List<List<Expression<*>>>,
): String {
    if (keys.isEmpty())
        throw NotImplementedError("Sync with empty keys is not implemented")
    val source = SelectValues(
        table = dest,
        columns = valuesColumns,
        values = valuesRows
    ).let { QueryAlias(it, "v") }
    val onKeys = keys.map { it.key eq it.value }
    val on = onKeys + valuesColumns.map { it eq source[it] }
    return mergeSelect(
        dest = dest,
        source = source,
        transaction = transaction,
        on = on.compoundAnd(),
        clauses = listOf(
            MergeStatement.Clause(
                type = MergeStatement.ClauseCondition.NOT_MATCHED,
                action = MergeStatement.ClauseAction.INSERT,
                arguments = keys.map { it.key to it.value } + valuesColumns.map { it to source[it] },
                and = null,
            ),
            MergeStatement.Clause(
                type = MergeStatement.ClauseCondition.NOT_MATCHED,
                action = MergeStatement.ClauseAction.DELETE,
                arguments = emptyList(),
                and = onKeys.compoundAnd(),
                overridingUserValue = true,
                overridingSystemValue = true
            ),
        ),
        prepared = true,
    ).replaceFirst(") WHEN NOT MATCHED AND (", ") WHEN NOT MATCHED BY SOURCE AND (")
}

private class SelectValues(
    table: Table,
    private val columns: List<Column<*>>,
    private val values: List<List<Expression<*>>>
) : AbstractQuery<SelectValues>(emptyList()) {
    override val set = Slice(table, columns)

    override fun prepareSQL(builder: QueryBuilder): String {
        require(values.isNotEmpty()) { "Can't prepare SQL for empty values" }
        builder {
            append("SELECT * FROM (VALUES ")
            values.forEachIndexed { idx0, row ->
                require(row.size == columns.size) { "Row size ${row.size} doesn't match column size ${columns.size}" }

                if (idx0 > 0) append(", ")
                append("(")
                row.forEachIndexed { index, expression ->
                    if (index > 0) append(", ")
                    append(expression)
                }
                append(")")
            }
            append(") AS _(")
            columns.forEachIndexed { index, column ->
                if (index > 0) append(", ")
                append(column.name)
            }
            append(")")
        }
        return builder.toString()
    }
}
