package de.quati.pgen.plugin.model.sql

import com.squareup.kotlinpoet.ClassName
import de.quati.pgen.plugin.util.codegen.CodeGenContext
import kotlinx.serialization.Serializable

@Serializable
data class Table(
    override val name: SqlObjectName,
    val columns: List<Column>,
    val primaryKey: PrimaryKey? = null,
    val foreignKeys: List<ForeignKey> = emptyList(),
    val uniqueConstraints: List<String> = emptyList(),
    val checkConstraints: List<String> = emptyList(),
) : SqlObject {
    context(c: CodeGenContext)
    val constraintsTypeName
        get() = ClassName("${name.packageName.name}.${name.prettyName}", "Constraints")

    context(c: CodeGenContext)
    val entityTypeName
        get() = ClassName("${name.packageName.name}.${name.prettyName}", "Entity")

    context(c: CodeGenContext)
    val updateEntityTypeName
        get() = ClassName("${name.packageName.name}.${name.prettyName}", "UpdateEntity")

    context(c: CodeGenContext)
    val createEntityTypeName
        get() = ClassName("${name.packageName.name}.${name.prettyName}", "CreateEntity")

    @Serializable
    data class PrimaryKey(val keyName: String, val columnNames: List<Column.Name>)

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
