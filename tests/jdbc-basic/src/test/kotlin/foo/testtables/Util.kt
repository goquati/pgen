package foo.testtables

import de.quati.pgen.jdbc.util.transaction
import de.quati.pgen.tests.jdbc.basic.createDb
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.deleteAll

internal val db = createDb(55430)

fun cleanUp(table: Table): Unit = runBlocking { db.transaction { table.deleteAll() } }
