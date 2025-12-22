package de.quati.pgen.tests.r2dbc.basic

import de.quati.pgen.r2dbc.util.r2dbcDatabasePooled
import de.quati.pgen.tests.r2dbc.basic.generated.db.pgenCodec
import io.r2dbc.postgresql.client.SSLMode
import io.r2dbc.spi.ValidationDepth
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import java.time.Duration

fun createDb(port: Int): R2dbcDatabase = r2dbcDatabasePooled {
    url("jdbc:postgresql://localhost:$port/postgres")
    codecs(pgenCodec)
    username("postgres")
    password("postgres")
    connectionConfig {
        if (it.host !in setOf("localhost"))
            sslMode(SSLMode.REQUIRE)
    }
    poolConfig {
        initialSize(2)
        maxSize(16)
        maxIdleTime(Duration.ofMinutes(15))
        backgroundEvictionInterval(Duration.ofMinutes(3))
        maxLifeTime(Duration.ofMinutes(60))
        maxCreateConnectionTime(Duration.ofSeconds(10))
        maxAcquireTime(Duration.ofSeconds(5))
        validationDepth(ValidationDepth.REMOTE)
    }
}