package de.quati.pgen.plugin.intern.tasks

import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.util.toFlywayOrNull
import org.gradle.api.logging.Logger


internal fun flywayMigration(
    config: Config,
    logger: Logger,
) {
    config.dbConfigs.forEach { dbConfig ->
        val flyway = dbConfig.toFlywayOrNull() ?: return@forEach
        logger.info("migrate db '${dbConfig.dbName}' with flyway")
        flyway.migrate()
    }
}