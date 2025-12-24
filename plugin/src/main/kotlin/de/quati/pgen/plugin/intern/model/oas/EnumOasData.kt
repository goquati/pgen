package de.quati.pgen.plugin.intern.model.oas

import com.squareup.kotlinpoet.ClassName
import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.sql.Enum

data class EnumOasData(
    val name: String,
    val items: List<String>,
    val sqlData: Enum,
) {
    val nameCapitalized = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    context(mapperConfig: Config.Oas.Mapper)
    fun getOasType() = ClassName(mapperConfig.packageOasModel, "${nameCapitalized}Dto")

    companion object {
        fun fromSqlData(data: Enum) = EnumOasData(
            name = data.name.prettyName,
            items = data.fields,
            sqlData = data,
        )
    }
}