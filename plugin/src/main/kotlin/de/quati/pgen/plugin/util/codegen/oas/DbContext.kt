package de.quati.pgen.plugin.util.codegen.oas

import de.quati.pgen.plugin.model.sql.DbName

interface DbContext {
    class Base(override val dbName: DbName) : DbContext
    val dbName: DbName
}
