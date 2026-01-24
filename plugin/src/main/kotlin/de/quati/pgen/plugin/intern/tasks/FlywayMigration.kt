package de.quati.pgen.plugin.intern.tasks

import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.util.toFlywayOrNull
import org.gradle.internal.logging.text.StyledTextOutput


internal fun flywayMigration(
    config: Config,
    out: StyledTextOutput,
) {
    config.dbConfigs.forEach { dbConfig ->
        val flyway = dbConfig.toFlywayOrNull() ?: return@forEach
        out.println("migrate db '${dbConfig.dbName}' with flyway")
        flyway.migrate()
    }
}