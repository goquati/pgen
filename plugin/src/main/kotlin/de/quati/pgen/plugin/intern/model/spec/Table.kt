package de.quati.pgen.plugin.intern.model.spec

import de.quati.pgen.plugin.intern.codegen.CodeGenContext
import kotlinx.serialization.Serializable

@Serializable
internal data class Table(
    override val name: SqlObjectName,
    val columns: List<Column>,
    val primaryKey: PrimaryKey? = null,
    val foreignKeys: List<ForeignKey> = emptyList(),
    val uniqueConstraints: List<UniqueConstraint> = emptyList(),
    val checkConstraints: List<CheckConstraint> = emptyList(),
) : SqlObject {
    context(_: CodeGenContext)
    val eventColumns
        get() = this@Table.columns.filter { it.type.isSupportedForWalEvents() }

    context(_: CodeGenContext)
    val isEventTable get() = eventColumns.isNotEmpty()

    context(_: CodeGenContext)
    val typeName
        get() = this@Table.name.packageName.className(this@Table.name.prettyName)

    context(_: CodeGenContext)
    val constraintsTypeName
        get() = this@Table.typeName.nestedClass("Constraints")

    context(_: CodeGenContext)
    val entityTypeName
        get() = this@Table.typeName.nestedClass("Entity")

    context(_: CodeGenContext)
    val eventTypeName
        get() = this@Table.typeName.nestedClass("Event")

    context(_: CodeGenContext)
    val eventEntityTypeName
        get() = this@Table.typeName.nestedClass("EventEntity")

    context(_: CodeGenContext)
    val updateEntityTypeName
        get() = this@Table.typeName.nestedClass("UpdateEntity")

    context(_: CodeGenContext)
    val createEntityTypeName
        get() = this@Table.typeName.nestedClass("CreateEntity")

    @Serializable
    data class PrimaryKey(val name: String, val columns: List<Column.Name>)


    @Serializable
    data class UniqueConstraint(val name: String)
    @Serializable
    data class CheckConstraint(val name: String)

    @Serializable
    data class ForeignKey(
        val name: String,
        val targetTable: SqlObjectName,
        val references: List<KeyPair>,
    ) {
        @Serializable
        data class KeyPair(
            val sourceColumn: Column.Name,
            val targetColumn: Column.Name,
        )

        fun toTyped() = if (references.size == 1)
            ForeignKeyTyped.SingleKey(
                name = name,
                targetTable = targetTable,
                reference = references.single(),
            )
        else
            ForeignKeyTyped.MultiKey(
                name = name,
                targetTable = targetTable,
                references = references,
            )
    }

    sealed interface ForeignKeyTyped {
        val name: String
        val targetTable: SqlObjectName

        data class SingleKey(
            override val name: String,
            override val targetTable: SqlObjectName,
            val reference: ForeignKey.KeyPair,
        ) : ForeignKeyTyped

        data class MultiKey(
            override val name: String,
            override val targetTable: SqlObjectName,
            val references: List<ForeignKey.KeyPair>,
        ) : ForeignKeyTyped
    }
}
