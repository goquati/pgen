package de.quati.pgen.plugin

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import de.quati.pgen.plugin.model.config.Config
import de.quati.pgen.plugin.model.oas.CommonOasData
import de.quati.pgen.plugin.model.oas.EnumOasData
import de.quati.pgen.plugin.model.oas.MetaOasData
import de.quati.pgen.plugin.model.oas.OasGenContext
import de.quati.pgen.plugin.model.oas.TableOasData
import de.quati.pgen.plugin.model.sql.Column
import de.quati.pgen.plugin.model.sql.PgenSpec
import de.quati.pgen.plugin.model.sql.Statement
import de.quati.pgen.plugin.service.DbService
import de.quati.pgen.plugin.service.DirectorySyncService
import de.quati.pgen.plugin.service.DirectorySyncService.Companion.directorySync
import de.quati.pgen.plugin.util.codegen.CodeGenContext
import de.quati.pgen.plugin.util.codegen.CodeGenContext.Companion.getColumnTypeGroups
import de.quati.pgen.plugin.util.codegen.sync
import de.quati.pgen.plugin.util.codegen.syncCodecs
import de.quati.pgen.plugin.util.codegen.oas.toOpenApi
import de.quati.pgen.plugin.util.codegen.syncQueries
import de.quati.pgen.plugin.util.parseStatements
import de.quati.pgen.plugin.util.toFlywayOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import kotlin.collections.asSequence
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText


private val yaml = Yaml(
    serializersModule = Yaml.default.serializersModule,
    configuration = YamlConfiguration(
        encodeDefaults = false,
        polymorphismStyle = PolymorphismStyle.Property,
    )
)

private fun generateSpec(config: Config) {
    val specData = config.dbConfigs.map { configDb ->
        DbService(
            dbName = configDb.dbName,
            connectionConfig = configDb.connectionConfig ?: error("no DB connection config defined")
        ).use { dbService ->
            val statements = dbService.getStatements(parseStatements(configDb.statementScripts))
            val tables = dbService.getTablesWithForeignTables(configDb.tableFilter)
            val allColumnTypes = tables.asSequence().flatMap { it.columns }.map { it.type }
                .map { if (it is Column.Type.NonPrimitive.Array) it.elementType else it }.toSet()
            val enumNames =
                allColumnTypes.filterIsInstance<Column.Type.NonPrimitive.Enum>().map { it.name }.toSet()
            val compositeTypeNames =
                allColumnTypes.filterIsInstance<Column.Type.NonPrimitive.Composite>().map { it.name }.toSet()
            val enums = dbService.getEnums(enumNames)
            val compositeTypes = dbService.getCompositeTypes(compositeTypeNames)
            PgenSpec(
                tables = tables,
                enums = enums,
                compositeTypes = compositeTypes,
                statements = statements,
            )
        }
    }
    val spec = PgenSpec(
        tables = specData.flatMap(PgenSpec::tables).sorted(),
        enums = specData.flatMap(PgenSpec::enums).sorted(),
        compositeTypes = specData.flatMap(PgenSpec::compositeTypes).sorted(),
        statements = specData.flatMap(PgenSpec::statements).sortedBy(Statement::name),
    )
    config.specFilePath.createParentDirectories().writeText(yaml.encodeToString(spec))
}

private fun getOasTables(config: Config, spec: PgenSpec): List<TableOasData> {
    val oasConfig = config.oasConfig ?: return emptyList()
    val tableConfigs = oasConfig.tables.associateBy { it.name }
    val oasTables = spec.tables.mapNotNull { table ->
        val tableConfig = tableConfigs[table.name] ?: return@mapNotNull null
        TableOasData.fromData(table, config = tableConfig)
    }
    return oasTables
}

private fun getOasCommon(config: Config, spec: PgenSpec): CommonOasData? {
    val oasTables = getOasTables(config, spec).takeIf { it.isNotEmpty() } ?: emptyList()
    val validEnumNames = oasTables.flatMap { it.fields }.map { it.type.getEnumNameOrNull() }
    val oasEnums = spec.enums.filter { it.name in validEnumNames }
        .map(EnumOasData::fromSqlData)
        .takeIf { it.isNotEmpty() } ?: return null
    val commonData = CommonOasData(enums = oasEnums)
    return commonData
}

private fun Config.loadSpec(): PgenSpec {
    if (specFilePath.notExists())
        error("Pgen spec file does not exist: '$specFilePath'")
    val spec = yaml.decodeFromString<PgenSpec>(specFilePath.readText())
    return spec
}

private fun generateOas(config: Config, spec: PgenSpec? = null) {
    val spec = spec ?: config.loadSpec()
    val oasConfig = config.oasConfig ?: return
    val oasTables = getOasTables(config, spec).takeIf { it.isNotEmpty() } ?: return
    val commonData = getOasCommon(config, spec)
    OasGenContext(
        pathPrefix = oasConfig.pathPrefix,
        meta = MetaOasData(
            title = oasConfig.title,
            version = oasConfig.version,
        ),
        oasCommonName = oasConfig.oasCommonName,
    ).run {
        println("sync oas files to ${oasConfig.oasRootPath}")
        directorySync(oasConfig.oasRootPath) {
            oasTables.forEach {
                sync(relativePath = it.nameCapitalized + ".yaml", content = it.toOpenApi())
            }
            if (commonData != null)
                sync(relativePath = oasConfig.oasCommonName + ".yaml", content = commonData.toOpenApi())
            cleanup()
        }
    }
}

private fun generateCode(config: Config) {
    val spec = config.loadSpec()
    generateOas(config, spec)

    CodeGenContext(
        rootPackageName = config.packageName,
        typeMappings = config.dbConfigs.flatMap(Config.Db::typeMappings)
            .associate { it.sqlType to it.valueClass },
        enumMappings = config.dbConfigs.flatMap(Config.Db::enumMappings)
            .associate { it.sqlType to it.enumClass },
        typeOverwrites = config.dbConfigs.flatMap(Config.Db::typeOverwrites)
            .associate { it.sqlColumn to it.valueClass },
        typeGroups = spec.tables.getColumnTypeGroups(),
        connectionType = config.connectionType,
        localConfigContext = config.oasConfig?.localConfigContext,
    ).run {
        println("sync code files to ${config.outputPath}")
        directorySync(config.outputPath) {
            spec.enums.forEach { sync(it) }
            syncCodecs(spec.enums)
            spec.compositeTypes.forEach { sync(it) }
            spec.domains.filter { it.name !in typeMappings }.forEach { sync(it) }
            spec.tables.map { it.update() }
                .also { syncQueries(it) }
                .forEach { sync(it) }
            spec.statements.groupBy { it.name.dbName }.values.forEach { sync(it) }
            syncOasMappers(config, spec)
            cleanup()
        }
    }
}

context(c: CodeGenContext)
private fun DirectorySyncService.syncOasMappers(config: Config, spec: PgenSpec) {
    val mapperConfig = config.oasConfig?.mapper ?: return
    val oasTables = getOasTables(config, spec).takeIf { it.isNotEmpty() } ?: return
    val enums = getOasCommon(config, spec)?.enums ?: emptyList()
    with(mapperConfig) {
        enums.forEach { sync(it) }
        oasTables.forEach { sync(it) }
    }
}

private fun flywayMigration(config: Config) {
    config.dbConfigs.forEach { dbConfig ->
        val flyway = dbConfig.toFlywayOrNull() ?: return@forEach
        println("migrate db '${dbConfig.dbName}' with flyway")
        flyway.migrate()
    }
}

@Suppress("unused")
class PgenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val configBuilder = project.extensions.create("pgen", Config.Builder::class.java)
        fun register(name: String, block: Task.(Config) -> Unit) = project.tasks.register(name) { task ->
            task.group = TASK_GROUP
            task.doLast { task ->
                val config = configBuilder.build()
                task.block(config)
            }
        }

        register("pgenGenerate") { config -> generate(config) }
        register("pgenGenerateSpec") { config -> generateSpec(config) }
        register("pgenGenerateCode") { config -> generateCode(config) }
        register("pgenGenerateOas") { config -> generateOas(config) }
        register("pgenFlywayMigration") { config -> flywayMigration(config) }
    }

    companion object {
        private const val TASK_GROUP = "quati tools"

        fun generate(config: Config) {
            generateSpec(config = config)
            generateCode(config = config)
        }
    }
}
