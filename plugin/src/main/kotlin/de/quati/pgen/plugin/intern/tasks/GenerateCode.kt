package de.quati.pgen.plugin.intern.tasks

import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.service.DirectorySyncService
import de.quati.pgen.plugin.intern.codegen.CodeGenContext
import de.quati.pgen.plugin.intern.codegen.sync
import de.quati.pgen.plugin.intern.codegen.syncQueries
import de.quati.pgen.plugin.intern.codegen.syncSchemaUtils
import org.gradle.api.file.Directory
import org.gradle.internal.logging.text.StyledTextOutput


internal fun generateCode(
    config: Config,
    outputPath: Directory,
    out: StyledTextOutput,
) {
    generateOas(config, out)
    DirectorySyncService(
        outDir = outputPath.dir(config.packageName.parts.joinToString("/")).asFile.toPath(),
        name = "pgen code",
        out = out,
    ).useWith {
        config.forEachCodeGenContext {
            dbSpec.enums.forEach { sync(it) }
            dbSpec.compositeTypes.forEach { sync(it) }
            dbSpec.domains.filter { it.ref !in typeMappings }.forEach { sync(it) }
            dbSpec.tables.map { it.update() }
                .also { syncQueries(it) }
                .forEach { sync(it) }
            sync(dbSpec.statements)
            syncSchemaUtils(dbSpec.allObjects)
            syncOasMappers()
        }
    }
}

context(c: CodeGenContext)
private fun DirectorySyncService.syncOasMappers() {
    val mapperConfig = c.oas?.mapper ?: return
    val oasTables = getOasTables().takeIf { it.isNotEmpty() } ?: return
    val enums = getOasCommon()?.enums ?: emptyList()
    with(mapperConfig) {
        enums.forEach { sync(it) }
        oasTables.forEach { sync(it) }
    }
}
