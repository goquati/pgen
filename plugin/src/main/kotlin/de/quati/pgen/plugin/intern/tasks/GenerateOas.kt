package de.quati.pgen.plugin.intern.tasks

import de.quati.kotlin.util.takeIfNotEmpty
import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.oas.CommonOasData
import de.quati.pgen.plugin.intern.model.oas.EnumOasData
import de.quati.pgen.plugin.intern.model.oas.MetaOasData
import de.quati.pgen.plugin.intern.model.oas.OasGenContext
import de.quati.pgen.plugin.intern.model.oas.TableOasData
import de.quati.pgen.plugin.intern.service.DirectorySyncService
import de.quati.pgen.plugin.intern.codegen.CodeGenContext
import de.quati.pgen.plugin.intern.codegen.oas.toOpenApi
import org.gradle.internal.logging.text.StyledTextOutput

internal fun generateOas(
    config: Config,
    out: StyledTextOutput,
) {
    val oasConfig = config.oas ?: return

    val oasData = config.codeGenContextSequence().mapNotNull { codeGenContext ->
        val oasTables = with(codeGenContext) {
            getOasTables().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        }
        val commonData = with(codeGenContext) { getOasCommon() }
        codeGenContext.dbConfig.dbName to (oasTables to commonData)
    }.toMap()

    val oasTables = oasData.values.flatMap { it.first }.takeIfNotEmpty() ?: return
    val commonData = oasData.values.flatMap { it.second?.enums ?: emptyList() }.takeIfNotEmpty()
        ?.let { CommonOasData(enums = it) }

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

context(c: CodeGenContext)
internal fun getOasTables(): List<TableOasData> {
    val oasConfig = c.oas ?: return emptyList()
    val tableConfigs = c.dbConfig.oasTables.map {
        it.copy(
            ignoreFields = it.ignoreFields + oasConfig.defaultIgnoreFields,
            ignoreFieldsAtUpdate = it.ignoreFieldsAtUpdate + oasConfig.defaultIgnoreFieldsAtUpdate,
            ignoreFieldsAtCreate = it.ignoreFieldsAtCreate + oasConfig.defaultIgnoreFieldsAtCreate,
        )
    }.associateBy { it.name }
    val oasTables = c.dbSpec.tables.mapNotNull { table ->
        val tableConfig = tableConfigs[table.name] ?: return@mapNotNull null
        TableOasData.fromData(table, config = tableConfig)
    }
    return oasTables
}

context(c: CodeGenContext)
internal fun getOasCommon(): CommonOasData? {
    val oasTables = getOasTables().takeIf { it.isNotEmpty() } ?: emptyList()
    val validEnumNames = oasTables.flatMap { it.fields }.map { it.type.getEnumNameOrNull() }
    val oasEnums = c.dbSpec.enums.filter { it.name in validEnumNames }
        .map(EnumOasData::fromSqlData)
        .takeIf { it.isNotEmpty() } ?: return null
    val commonData = CommonOasData(enums = oasEnums)
    return commonData
}