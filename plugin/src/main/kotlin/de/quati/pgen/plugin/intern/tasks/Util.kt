package de.quati.pgen.plugin.intern.tasks

import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.spec.PgenSpec
import de.quati.pgen.plugin.intern.codegen.CodeGenContext
import kotlinx.serialization.json.Json
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.dataformat.yaml.YAMLWriteFeature
import java.nio.file.Path
import kotlin.io.path.notExists

private val yamlMapper get() = YAMLMapper.builder().disable(YAMLWriteFeature.WRITE_DOC_START_MARKER).build()
private val jsonMapper get() = JsonMapper.builder().build()
private val kotlinJsonMapper get() = Json { encodeDefaults = false }

internal fun PgenSpec.encodeToYaml(): String {
    val jsonStr = kotlinJsonMapper.encodeToString(this)
    val kotlinObj = jsonMapper.readValue(jsonStr, Any::class.java)
    val yamlStr = yamlMapper.writeValueAsString(kotlinObj)
    return yamlStr
}

private fun Path.readPgenSpec(): PgenSpec {
    if (notExists())
        error("Pgen spec file does not exist: '$this'")
    val kotlinObj = yamlMapper.readValue(this.toFile(), Any::class.java)
    val jsonStr = jsonMapper.writeValueAsString(kotlinObj)
    val spec = kotlinJsonMapper.decodeFromString<PgenSpec>(jsonStr)
    return spec
}

internal fun Config.forEachCodeGenContext(block: CodeGenContext.() -> Unit) {
    codeGenContextSequence().forEach { codeGenContext ->
        codeGenContext.block()
    }
}

internal fun Config.codeGenContextSequence(): Sequence<CodeGenContext> {
    val spec = specFilePath.readPgenSpec()
    val configDbs = this.dbConfigs.associateBy { it.dbName }
    val specDbs = spec.databases.associateBy { it.name }
    require(configDbs.keys == specDbs.keys) {
        "Config and spec databases must have the same keys, $configDbs != $specDbs"
    }
    val globalConfig = toGlobalConfig()
    return configDbs.entries.asSequence().map { (dbName, dbConfig) ->
        val dbSpec = specDbs[dbName] ?: error("DB '$dbName' not found in spec")
        CodeGenContext(
            globalConfig = globalConfig,
            dbConfig = dbConfig,
            dbSpec = dbSpec,
            oas = this.oas,
        )
    }
}