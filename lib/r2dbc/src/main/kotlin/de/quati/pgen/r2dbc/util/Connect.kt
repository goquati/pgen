package de.quati.pgen.r2dbc.util

import de.quati.pgen.shared.ConnectionConfig
import de.quati.pgen.shared.IConnectionProperties
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.extension.CodecRegistrar
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import kotlin.collections.forEach

public fun R2dbcDatabase.Companion.connect(
    config: ConnectionConfig.Async,
    username: String? = null,
    password: String? = null,
    block: PostgresqlConnectionConfiguration.Builder.() -> Unit = {},
): R2dbcDatabase {
    val options = PostgresqlConnectionConfiguration.builder().apply {
        host(config.host)
        config.port?.let { port(it) }
        database(config.database)
        username?.let { username(it) }
        password?.let { password(it) }
        block()
    }.build()
    val cxFactory = PostgresqlConnectionFactory(options)
    val db = R2dbcDatabase.connect(
        connectionFactory = cxFactory,
        databaseConfig = R2dbcDatabaseConfig {
            explicitDialect = PostgreSQLDialect()
        }
    )
    return db
}

public fun IConnectionProperties.toR2dbcDatabase(
    codecs: List<CodecRegistrar> = emptyList(),
    connectionConfig: PostgresqlConnectionConfiguration.Builder.(ConnectionConfig) -> Unit = {},
    databaseConfig: R2dbcDatabaseConfig.Builder.(ConnectionConfig) -> Unit = {},
): R2dbcDatabase {
    val config = toConnectionConfig().toJdbc()
    val options = PostgresqlConnectionConfiguration.builder().apply {
        host(config.host)
        config.port?.let(::port)
        database(config.database)
        username(username)
        password(password)
        codecs.forEach(::codecRegistrar)
        connectionConfig(config)
    }.build()
    val cxFactory = PostgresqlConnectionFactory(options)
    val db = R2dbcDatabase.connect(
        connectionFactory = cxFactory,
        databaseConfig = R2dbcDatabaseConfig {
            explicitDialect = PostgreSQLDialect()
            databaseConfig(config)
        }
    )
    return db
}