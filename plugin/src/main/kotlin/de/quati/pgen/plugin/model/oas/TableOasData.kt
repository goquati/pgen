package de.quati.pgen.plugin.model.oas

import com.squareup.kotlinpoet.ClassName
import de.quati.pgen.plugin.model.config.Config
import de.quati.pgen.plugin.model.sql.Table

private infix fun <T> Set<T>.anyIn(other: Set<T>) = this.intersect(other).isNotEmpty()

data class TableOasData(
    private val name: String,
    val fields: List<TableFieldOasData>,
    val endpoints: Set<Config.OasConfig.CRUD>,
    val sqlData: Table,
) {
    val idFormat = fields.singleOrNull { it.name == "id" }?.type?.let { it as? TableFieldTypeOasData.Type }
        .also { if (it == null) error("'id' field not found for table $name") }
        ?.format
    val namePretty = name.replaceFirstChar { if (it.isTitleCase()) it.lowercase() else it.toString() }
    val path = namePretty
    val tag = namePretty
    val nameCapitalized = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    val fieldsAtCreate get() = fields.filterNot { it.ignoreAtCreate }
    val fieldsAtUpdate get() = fields.filterNot { it.ignoreAtUpdate }

    context(mapperConfig: Config.OasConfig.Mapper)
    fun getOasReadType() = ClassName(mapperConfig.packageOasModel, "${nameCapitalized}Dto")

    context(mapperConfig: Config.OasConfig.Mapper)
    fun getOasUpdateType() = ClassName(mapperConfig.packageOasModel, "${nameCapitalized}UpdateDto")

    context(mapperConfig: Config.OasConfig.Mapper)
    fun getOasCreateType() = ClassName(mapperConfig.packageOasModel, "${nameCapitalized}CreateDto")

    fun getOasServiceName() = "I${nameCapitalized}Service"
    companion object {
        fun fromData(data: Table, config: Config.OasConfig.Table): TableOasData {
            val fields = data.columns.mapNotNull { column ->
                val possibleNames = setOf(column.name.value, column.name.pretty)
                if (possibleNames anyIn config.ignoreFields) return@mapNotNull null
                TableFieldOasData(
                    name = column.prettyName,
                    nullable = column.isNullable,
                    ignoreAtCreate = possibleNames anyIn config.ignoreFieldsAtCreate,
                    ignoreAtUpdate = possibleNames anyIn config.ignoreFieldsAtUpdate,
                    type = TableFieldTypeOasData.fromData(column.type),
                    sqlData = column,
                )
            }

            return TableOasData(
                name = data.name.prettyName,
                fields = fields,
                endpoints = Config.OasConfig.CRUD.entries.filter { it !in config.ignoreMethods }.toSet(),
                sqlData = data,
            )
        }
    }
}