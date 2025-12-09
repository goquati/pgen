package de.quati.pgen.r2dbc.util

import de.quati.pgen.shared.ConnectionConfig
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.extension.CodecRegistrar
import org.jetbrains.exposed.v1.core.transactions.TransactionManagerApi
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import kotlin.collections.addAll
import kotlin.collections.forEach

public fun r2dbcDatabasePooled(block: R2dbcDatabasePooledBuilder.() -> Unit): R2dbcDatabase =
    R2dbcDatabasePooledBuilder().apply(block).build()

public class R2dbcDatabasePooledBuilder {
    private var url: ConnectionConfig? = null
    private val codecs: MutableList<CodecRegistrar> = mutableListOf()
    private var username: String? = null
    private var password: String? = null
    private var connectionConfig: PostgresqlConnectionConfiguration.Builder.(ConnectionConfig) -> Unit = {}
    private var poolConfig: ConnectionPoolConfiguration.Builder.() -> Unit = {}
    private var dbConfig: R2dbcDatabaseConfig.Builder.() -> Unit = {}
    private var transactionManager: (R2dbcDatabase) -> TransactionManagerApi = { TransactionManager(it) }

    public fun url(value: String): R2dbcDatabasePooledBuilder = apply { this.url = ConnectionConfig.parse(value) }
    public fun username(value: String): R2dbcDatabasePooledBuilder = apply { this.username = value }
    public fun password(value: String): R2dbcDatabasePooledBuilder = apply { this.password = value }
    public fun codecs(values: Iterable<CodecRegistrar>): R2dbcDatabasePooledBuilder = apply { codecs.addAll(values) }
    public fun poolConfig(block: ConnectionPoolConfiguration.Builder.() -> Unit): R2dbcDatabasePooledBuilder =
        apply { poolConfig = block }

    public fun dbConfig(block: R2dbcDatabaseConfig.Builder.() -> Unit): R2dbcDatabasePooledBuilder =
        apply { dbConfig = block }

    public fun transactionManager(block: (R2dbcDatabase) -> TransactionManagerApi): R2dbcDatabasePooledBuilder =
        apply { transactionManager = block }

    public fun connectionConfig(
        block: PostgresqlConnectionConfiguration.Builder.(ConnectionConfig) -> Unit,
    ): R2dbcDatabasePooledBuilder = apply { connectionConfig = block }

    public fun build(): R2dbcDatabase {
        val url = url
        checkNotNull(url) { "url is required" }
        val options = PostgresqlConnectionConfiguration.builder().apply {
            host(url.host)
            url.port?.let(::port)
            database(url.database)
            codecs.forEach(::codecRegistrar)
            username?.also(::username)
            password?.also(::password)
            connectionConfig(url)
        }.build()
        val cxFactory = PostgresqlConnectionFactory(options)
        val poolConfig = ConnectionPoolConfiguration.builder(cxFactory).apply(poolConfig).build()
        val pooledFactory = ConnectionPool(poolConfig)
        return R2dbcDatabase.connect(
            connectionFactory = pooledFactory,
            databaseConfig = R2dbcDatabaseConfig {
                explicitDialect = PostgreSQLDialect()
                dbConfig()
            },
            manager = transactionManager
        )
    }
}