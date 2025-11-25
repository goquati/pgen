package io.github.klahap.pgen.util

import io.github.klahap.pgen.model.config.Config
import io.github.klahap.pgen.util.codegen.CodeGenContext

sealed interface StaticCodeFile {
    val relativePackageNames: List<String>
    val fileName: String

    data class SharedCodeFile(
        override val relativePackageNames: List<String>,
        override val fileName: String,
    ) : StaticCodeFile

    data class DefaultCodeFile(
        override val relativePackageNames: List<String>,
        override val fileName: String,
    ) : StaticCodeFile


    val relativePath get() = (relativePackageNames + listOf(fileName)).joinToString("/")
    private val rootPath
        get() = when (this) {
            is DefaultCodeFile -> "/default_code"
            is SharedCodeFile -> "/shared_code"
        }

    context(c: CodeGenContext)
    fun getContent(): String = javaClass.getResourceAsStream("$rootPath/$relativePath")!!
        .readAllBytes().decodeToString()
        .replaceFirst("package default_code", "package ${c.poet.rootPackageName}")
        .replace("import default_code", "import ${c.poet.rootPackageName}")
        .replaceFirst("package shared_code", "package ${c.poet.sharedPackageName}")
        .replace("import shared_code", "import ${c.poet.sharedPackageName}")

    companion object Companion {
        @JvmName("yieldSharedCodeFile")
        suspend fun SequenceScope<SharedCodeFile>.yield(relativePackageNames: List<String>, fileName: String) {
            yield(SharedCodeFile(relativePackageNames = relativePackageNames, fileName = fileName))
        }

        @JvmName("yieldDefaultCodeFile")
        suspend fun SequenceScope<DefaultCodeFile>.yield(relativePackageNames: List<String>, fileName: String) {
            yield(DefaultCodeFile(relativePackageNames = relativePackageNames, fileName = fileName))
        }

        fun allSharedCode(config: Config) = sequence<SharedCodeFile> {
            yield(listOf(), "RegClass.kt")
            yield(listOf(), "LocalConfigContext.kt")
            yield(listOf(), "StringLike.kt")
        }

        fun allDefaultCode(config: Config) = sequence<DefaultCodeFile> {
            yield(listOf("column_type"), "DefaultJsonColumnType.kt")
            yield(listOf("column_type"), "DomainColumnType.kt")
            yield(listOf("column_type"), "PgStructUtil.kt")
            yield(listOf("column_type"), "UnconstrainedNumericColumnType.kt")
            yield(listOf("column_type"), "Util.kt")
            yield(listOf("column_type"), "PgVectorColumnType.kt")
            yield(listOf("column_type"), "RegClass.kt")
            if (config.connectionType == Config.ConnectionType.JDBC) {
                yield(listOf("column_type"), "IntMultiRange.kt")
                yield(listOf("column_type"), "IntRange.kt")
                yield(listOf("column_type"), "MultiRange.kt")
                yield(listOf("column_type"), "MultiRangeColumnType.kt")
                yield(listOf("column_type"), "RangeColumnType.kt")
            }
            yield(listOf("util"), "BatchUpdateStatementDsl.kt")
            yield(listOf("util"), "Dsl.kt")
            yield(listOf("util"), "ConnectionConfig.kt")
            yield(listOf("util"), "IConnectionProperties.kt")
            yield(listOf("util"), "PgenException.kt")
            when (config.connectionType) {
                Config.ConnectionType.JDBC -> {
                    yield(listOf("util"), "JdbcDsl.kt")
                    yield(listOf("column_type"), "UtilJdbc.kt")
                }

                Config.ConnectionType.R2DBC -> {
                    yield(listOf("util"), "R2dbcDsl.kt")
                    yield(listOf("util"), "R2dbcSyncStatement.kt")
                    yield(listOf("util"), "R2dbcExceptionUtil.kt")
                    yield(listOf("column_type"), "UtilR2dbc.kt")
                }
            }
            if (config.addJacksonV2Utils)
                yield(listOf("util"), "JacksonUtilV2.kt")
            if (config.addJacksonV3Utils)
                yield(listOf("util"), "JacksonUtilV3.kt")
            if (config.addKotlinxJsonUtils)
                yield(listOf("util"), "KotlinxJsonUtil.kt")
            if (config.addJacksonV2Utils && config.addKotlinxJsonUtils)
                yield(listOf("util"), "QuatiStringSerializerJacksonV2.kt")
            if (config.addJacksonV3Utils && config.addKotlinxJsonUtils)
                yield(listOf("util"), "QuatiStringSerializerJacksonV3.kt")
        }
    }
}
