package de.quati.pgen.plugin.util

import de.quati.pgen.plugin.model.config.Config
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource


fun Config.Db.toFlywayOrNull(): Flyway? {
    val connConfig = connection ?: return null
    val flywayConfig = flyway ?: return null
    val dataSource = PGSimpleDataSource().apply {
        setUrl(connConfig.url)
        user = connConfig.user
        password = connConfig.password
    }
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("filesystem:${flywayConfig.migrationDirectory}")
        .load() ?: error("no flyway instance")
}