package foo.testtables

import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.tests.r2dbc.basic.createDb
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.r2dbc.deleteAll

internal val db = createDb("r2dbc_basic")

fun cleanUp(table: Table): Unit = runBlocking { db.suspendTransaction { table.deleteAll() } }
