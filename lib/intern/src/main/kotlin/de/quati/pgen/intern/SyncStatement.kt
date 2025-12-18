package de.quati.pgen.intern

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
import org.jetbrains.exposed.v1.core.targetTables
import org.jetbrains.exposed.v1.core.vendors.FunctionProvider

public class SyncRow(
    public val columns: List<Column<*>>,
    public val values: List<Expression<*>>
)

public class SyncKeysBuilder {
    public val keys: MutableMap<Column<*>, QueryParameter<*>> = mutableMapOf()

    public operator fun <T> set(column: Column<T>, value: T) {
        keys[column] = QueryParameter(value, column.columnType)
    }
}

public abstract class SyncBuilder<out T>(targets: List<Table>) : Statement<T>(StatementType.MERGE, targets) {
    public var builderValuesColumns: List<Column<*>>? = null
    public val builderValuesRows: MutableList<List<Expression<*>>> = mutableListOf()

    public fun addRow(row: SyncRow) {
        if (builderValuesColumns == null)
            builderValuesColumns = row.columns
        else
            require(builderValuesColumns == row.columns) {
                "Columns of current row don't match columns of previous values"
            }
        builderValuesRows.add(row.values)
    }
}

public class SyncStatement(
    public val targetsSet: Table,
    public val keys: Map<Column<*>, QueryParameter<*>>,
) : SyncBuilder<Unit>(targetsSet.targetTables()) {
    public val valuesColumns: List<Column<*>> get() = builderValuesColumns ?: emptyList()
    public val valuesRows: List<List<Expression<*>>> get() = builderValuesRows


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
