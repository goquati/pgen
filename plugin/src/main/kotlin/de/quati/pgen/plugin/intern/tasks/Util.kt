package de.quati.pgen.plugin.intern.tasks

import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.spec.PgenSpec
import de.quati.pgen.plugin.intern.codegen.CodeGenContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.notExists
import kotlin.io.path.readText

private val kotlinJsonMapper
    get() = Json {
        encodeDefaults = false
        prettyPrint = true
        @OptIn(ExperimentalSerializationApi::class)
        prettyPrintIndent = "  "
    }

internal fun PgenSpec.encodeToYaml(): String {
    val jsonStr = kotlinJsonMapper.encodeToString(this)
    return jsonStr
}

private fun Path.readPgenSpec(): PgenSpec {
    if (notExists())
        error("Pgen spec file does not exist: '$this'")
    val spec = kotlinJsonMapper.decodeFromString<PgenSpec>(readText())
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
