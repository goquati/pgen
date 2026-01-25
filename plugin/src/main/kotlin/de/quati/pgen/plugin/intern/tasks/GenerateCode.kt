package de.quati.pgen.plugin.intern.tasks

import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.sql.PgenSpec
import de.quati.pgen.plugin.intern.service.DirectorySyncService
import de.quati.pgen.plugin.intern.util.codegen.CodeGenContext
import de.quati.pgen.plugin.intern.util.codegen.CodeGenContext.Companion.getColumnTypeGroups
import de.quati.pgen.plugin.intern.util.codegen.sync
import de.quati.pgen.plugin.intern.util.codegen.syncQueries
import de.quati.pgen.plugin.intern.util.codegen.syncSchemaUtils
import org.gradle.api.file.Directory
import org.gradle.internal.logging.text.StyledTextOutput


internal fun generateCode(
    config: Config,
    outputPath: Directory,
    out: StyledTextOutput,
) {
    val spec = config.loadSpec()
    generateOas(config, spec, out)

    CodeGenContext(
        config = config,
        typeGroups = spec.tables.getColumnTypeGroups(),
    ).run {
        DirectorySyncService(
            outDir = outputPath.dir(config.packageName.parts.joinToString("/")).asFile.toPath(),
            name = "pgen code",
            out = out,
        ).useWith {
            spec.enums.forEach { sync(it) }
            spec.compositeTypes.forEach { sync(it) }
            spec.domains.filter { it.name !in typeMappings }.forEach { sync(it) }
            spec.tables.map { it.update() }
                .also { syncQueries(it) }
                .forEach { sync(it) }
            spec.statements.groupBy { it.name.dbName }.values.forEach { sync(it) }
            syncSchemaUtils(spec.allObjects)
            syncOasMappers(config, spec)
        }
    }
}

context(c: CodeGenContext)
private fun DirectorySyncService.syncOasMappers(config: Config, spec: PgenSpec) {
    val mapperConfig = config.oas?.mapper ?: return
    val oasTables = getOasTables(config, spec).takeIf { it.isNotEmpty() } ?: return
    val enums = getOasCommon(config, spec)?.enums ?: emptyList()
    with(mapperConfig) {
        enums.forEach { sync(it) }
        oasTables.forEach { sync(it) }
    }
}
