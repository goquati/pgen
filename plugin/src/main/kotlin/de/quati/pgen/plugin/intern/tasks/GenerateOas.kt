package de.quati.pgen.plugin.intern.tasks

import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.oas.CommonOasData
import de.quati.pgen.plugin.intern.model.oas.EnumOasData
import de.quati.pgen.plugin.intern.model.oas.MetaOasData
import de.quati.pgen.plugin.intern.model.oas.OasGenContext
import de.quati.pgen.plugin.intern.model.oas.TableOasData
import de.quati.pgen.plugin.intern.model.sql.PgenSpec
import de.quati.pgen.plugin.intern.service.DirectorySyncService
import de.quati.pgen.plugin.intern.util.codegen.SpecContext
import de.quati.pgen.plugin.intern.util.codegen.oas.toOpenApi
import org.gradle.internal.logging.text.StyledTextOutput

internal fun generateOas(
    config: Config,
    spec: PgenSpec? = null,
    out: StyledTextOutput,
) {
    val spec = spec ?: config.loadSpec()
    val oasConfig = config.oas ?: return
    val specContext = spec.toSpecContext(config)
    val oasTables = with(specContext) { getOasTables(config, spec).takeIf { it.isNotEmpty() } } ?: return
    val commonData = with(specContext) { getOasCommon(config, spec) }
    OasGenContext(
        pathPrefix = oasConfig.pathPrefix,
        meta = MetaOasData(
            title = oasConfig.title,
            version = oasConfig.version,
        ),
        oasCommonName = oasConfig.oasCommonName,
    ).run {
        DirectorySyncService(
            outDir = oasConfig.oasRootPath,
            name = "OpenApi files",
            out = out,
        ).useWith {
            oasTables.forEach {
                sync(relativePath = it.nameCapitalized + ".yaml", content = it.toOpenApi())
            }
            if (commonData != null)
                sync(relativePath = oasConfig.oasCommonName + ".yaml", content = commonData.toOpenApi())
        }
    }
}

context(_: SpecContext)
internal fun getOasTables(config: Config, spec: PgenSpec): List<TableOasData> {
    val oasConfig = config.oas ?: return emptyList()
    val tableConfigs = oasConfig.tables.associateBy { it.name }
    val oasTables = spec.tables.mapNotNull { table ->
        val tableConfig = tableConfigs[table.name] ?: return@mapNotNull null
        with(table.dbName.toContext()) {
            TableOasData.fromData(table, config = tableConfig)
        }
    }
    return oasTables
}

context(_: SpecContext)
internal fun getOasCommon(config: Config, spec: PgenSpec): CommonOasData? {
    val oasTables = getOasTables(config, spec).takeIf { it.isNotEmpty() } ?: emptyList()
    val validEnumNames = oasTables.flatMap { it.fields }.map { it.type.getEnumNameOrNull() }
    val oasEnums = spec.enums.filter { it.name in validEnumNames }
        .map(EnumOasData::fromSqlData)
        .takeIf { it.isNotEmpty() } ?: return null
    val commonData = CommonOasData(enums = oasEnums)
    return commonData
}