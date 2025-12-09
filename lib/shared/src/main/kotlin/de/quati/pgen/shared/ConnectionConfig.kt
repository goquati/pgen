package de.quati.pgen.shared

import kotlin.time.Duration

public sealed interface ConnectionConfig {
    public val subProtocol: String
    public val host: String
    public val port: Int?
    public val database: String
    public val query: Map<String, String>

    public sealed interface Async : ConnectionConfig

    private val protocol
        get() = when (this) {
            is R2dbcPool -> Protocol.R2DBC_POOL
            is R2dbc -> Protocol.R2DBC
            is Jdbc -> Protocol.JDBC
        }

    private val queryStr
        get() = query.entries.joinToString(
            prefix = if (query.isEmpty()) "" else "?",
            separator = "&",
        ) { "${it.key}=${it.value}" }

    public fun url(): String = url(creds = null)
    public fun url(user: String, password: String): String = url(creds = "$user:$password")
    private fun url(creds: String?): String =
        "${protocol.value}:$subProtocol://${creds?.let { "$it@" } ?: ""}$host:$port/$database$queryStr"

    public fun toR2dbcPool(block: R2dbcPool.Builder.() -> Unit = {}): R2dbcPool = R2dbcPool.Builder(
        subProtocol = subProtocol,
        host = host,
        port = port,
        database = database,
        query = query.toMutableMap(),
    ).apply(block).build()

    public fun toR2dbc(block: R2dbc.Builder.() -> Unit = {}): R2dbc = R2dbc.Builder(
        subProtocol = subProtocol,
        host = host,
        port = port,
        database = database,
        query = query.toMutableMap(),
    ).apply(block).build()

    public fun toJdbc(block: Jdbc.Builder.() -> Unit = {}): Jdbc = Jdbc.Builder(
        subProtocol = subProtocol,
        host = host,
        port = port,
        database = database,
        query = query.toMutableMap(),
    ).apply(block).build()


    private enum class Protocol(val value: String) {
        JDBC("jdbc"),
        R2DBC("r2dbc"),
        R2DBC_POOL("r2dbc:pool"),
    }

    public data class R2dbcPool(
        override val subProtocol: String,
        override val host: String,
        override val port: Int?,
        override val database: String,
        override val query: Map<String, String>,
    ) : Async {
        public data class Builder(
            var subProtocol: String,
            var host: String,
            var port: Int?,
            var database: String,
            val query: MutableMap<String, String>,
        ) {
            /**
             * Number of retries if the first connection acquisition attempt fails. Defaults to 1.
             */
            public fun acquireRetry(value: Int) {
                query["acquireRetry"] = value.toString()
            }

            /**
             * Interval for background eviction enabling background eviction. Disabled by default. Setting the value to
             * Duration.ZERO disables background eviction even if maxIdleTime is configured.
             */
            public fun backgroundEvictionInterval(interval: Duration) {
                query["backgroundEvictionInterval"] = interval.toIsoString()
            }

            /**
             * Initial pool size. Defaults to 10.
             */
            public fun initialSize(value: Int) {
                query["initialSize"] = value.toString()
            }

            /**
             *	Minimum idle connection count. Defaults to 0.
             */
            public fun minIdle(value: Int) {
                query["minIdle"] = value.toString()
            }

            /**
             *	Maximum pool size. Defaults to 10.
             */
            public fun maxSize(value: Int) {
                query["maxSize"] = value.toString()
            }

            /**
             * Maximum lifetime of the connection in the pool. Negative values indicate no timeout. Defaults to no timeout.
             */
            public fun maxLifeTime(value: Duration) {
                query["maxLifeTime"] = value.toIsoString()
            }

            /**
             * Maximum idle time of the connection in the pool. Negative values indicate no timeout. Defaults to 30 minutes.
             * This value is used as an interval for background eviction of idle connections unless configuring backgroundEvictionInterval.
             */
            public fun maxIdleTime(value: Duration) {
                query["maxIdleTime"] = value.toIsoString()
            }

            /**
             * Maximum time to acquire connection from pool. Negative values indicate no timeout. Defaults to no timeout.
             */
            public fun maxAcquireTime(value: Duration) {
                query["maxAcquireTime"] = value.toIsoString()
            }

            /**
             * Maximum time to create a new connection. Negative values indicate no timeout. Defaults to no timeout.
             */
            public fun maxCreateConnectionTime(value: Duration) {
                query["maxCreateConnectionTime"] = value.toIsoString()
            }

            /**
             * Maximum time to validate connection from pool. Negative values indicate no timeout. Defaults to no timeout.
             */
            public fun maxValidationTime(value: Duration) {
                query["maxValidationTime"] = value.toIsoString()
            }

            public fun build(): R2dbcPool = R2dbcPool(
                subProtocol = subProtocol,
                host = host,
                port = port,
                database = database,
                query = query.toMap(),
            )
        }
    }

    public data class R2dbc(
        override val subProtocol: String,
        override val host: String,
        override val port: Int?,
        override val database: String,
        override val query: Map<String, String>,
    ) : Async {
        public data class Builder(
            var subProtocol: String,
            var host: String,
            var port: Int?,
            var database: String,
            val query: MutableMap<String, String>,
        ) {
            public fun build(): R2dbc = R2dbc(
                subProtocol = subProtocol,
                host = host,
                port = port,
                database = database,
                query = query.toMap(),
            )
        }
    }

    public data class Jdbc(
        override val subProtocol: String,
        override val host: String,
        override val port: Int?,
        override val database: String,
        override val query: Map<String, String>,
    ) : ConnectionConfig {
        public data class Builder(
            var subProtocol: String,
            var host: String,
            var port: Int?,
            var database: String,
            val query: MutableMap<String, String>,
        ) {
            public fun build(): Jdbc = Jdbc(
                subProtocol = subProtocol,
                host = host,
                port = port,
                database = database,
                query = query.toMap(),
            )
        }
    }

    public companion object {
        public fun parse(url: String): ConnectionConfig {
            val regex = Regex("""([\w:]+):(\w+)://([^:/]+)(?::(\d+))?/([\w-]+)(?:\?(.+))?""")
            val match = regex.matchEntire(url)
                ?: throw IllegalArgumentException("Unsupported or invalid DB URL")
            val (protocolStr, subProtocol, host, portStr, database, queryStr) = match.destructured
            val protocol = Protocol.entries.firstOrNull { it.value.equals(protocolStr, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unsupported protocol '$protocolStr'")
            val query = queryStr.split("&").filter { '=' in it }
                .associate { it.substringBefore('=') to it.substringAfter('=') }
            val connectionConfig = when (protocol) {
                Protocol.R2DBC_POOL -> R2dbcPool(
                    subProtocol = subProtocol,
                    host = host,
                    port = portStr.toIntOrNull(),
                    database = database,
                    query = query,
                )

                Protocol.JDBC -> Jdbc(
                    subProtocol = subProtocol,
                    host = host,
                    port = portStr.toIntOrNull(),
                    database = database,
                    query = query,
                )

                Protocol.R2DBC -> R2dbc(
                    subProtocol = subProtocol,
                    host = host,
                    port = portStr.toIntOrNull(),
                    database = database,
                    query = query,
                )
            }
            return connectionConfig
        }
    }
}
