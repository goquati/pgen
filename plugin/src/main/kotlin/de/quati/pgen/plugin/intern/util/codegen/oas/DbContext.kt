package de.quati.pgen.plugin.intern.util.codegen.oas

import de.quati.pgen.plugin.intern.model.sql.DbName

interface DbContext {
    class Base(override val dbName: DbName) : DbContext
    val dbName: DbName
}
