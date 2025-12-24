package de.quati.pgen.plugin.intern.model.config

import com.squareup.kotlinpoet.ClassName
import de.quati.pgen.plugin.intern.dsl.PackageName
import de.quati.pgen.plugin.intern.model.sql.DbName
import de.quati.pgen.plugin.intern.model.sql.SqlObjectName
import java.nio.file.Path


data class Config(
    val dbConfigs: List<Db>,
    val packageName: PackageName,
    val outputPath: Path,
    val specFilePath: Path,
    val connectionType: ConnectionType,
    val oas: Oas?,
) {
    enum class ConnectionType {
        JDBC, R2DBC
    }

    data class Oas(
        val title: String,
        val version: String,
        val oasRootPath: Path,
        val oasCommonName: String,
        val pathPrefix: String,
        val mapper: Mapper?,
        val tables: List<Table>,
        val localConfigContext: LocalConfigContext?,
    ) {
        data class LocalConfigContext(
            val type: ClassName,
            val atMethods: Set<CRUD>,
        )

        data class Table(
            val name: SqlObjectName,
            val ignoreFields: Set<String>,
            val ignoreFieldsAtCreate: Set<String>,
            val ignoreFieldsAtUpdate: Set<String>,
            val ignoreMethods: Set<CRUD>,
        )

        data class Mapper(
            val packageOasModel: String,
        )

        enum class CRUD {
            CREATE, READ, READ_ALL, UPDATE, DELETE
        }
    }

    data class Db(
        val dbName: DbName,
        val connection: Connection?,
        val tableFilter: SqlObjectFilter,
        val statementScripts: Set<Path>,
        val typeMappings: Set<TypeMapping>,
        val enumMappings: Set<EnumMapping>,
        val typeOverwrites: Set<TypeOverwrite>,
        val columnTypeMappings: Set<ColumnTypeMapping>,
        val flyway: Flyway?,
    ) {
        data class Flyway(
            val migrationDirectory: Path,
        )

        data class Connection(
            val url: String,
            val user: String,
            val password: String,
        )
    }
}