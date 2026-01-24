package de.quati.pgen.example.r2dbc

import de.quati.kotlin.util.Option
import de.quati.pgen.example.r2dbc.generated.db.base.public1.BaseSchemaPublic
import de.quati.pgen.example.r2dbc.generated.db.base.public1.NonEmptyText
import de.quati.pgen.example.r2dbc.generated.db.base.public1.Users
import de.quati.pgen.r2dbc.util.emitLogicalPgMessage
import de.quati.pgen.r2dbc.util.r2dbcDatabasePooled
import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.shared.WalEvent
import de.quati.pgen.shared.WalEvent.Change.Payload
import de.quati.pgen.wal.PgenWalEventListener
import de.quati.pgen.wal.pgenWalListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insertReturning
import org.jetbrains.exposed.v1.r2dbc.update
import org.postgresql.PGProperty
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * This example demonstrates how to use the pgen WAL (Write-Ahead Log) listener with R2DBC and Exposed.
 *
 * It starts a listener that monitors changes to the schema and prints them to the console.
 * Then it executes a series of queries (INSERT, UPDATE, DELETE) to trigger these events.
 * Finally, it sends a logical message via PostgreSQL to signal the listener to stop.
 */
suspend fun main() {
    val listener = setupWalListener()
    val database = setupDatabase()
    val readyFlag = ReadyFlag()

    coroutineScope {
        // 1. Start the WAL listener in a background job
        val listenerJob = launch {
            println("--- WAL Listener started ---")
            listener.flow
                .onSubscription { readyFlag.markReady() }
                .onStart { println("Waiting for events...") }
                // We use a special logical message "QUIT" to stop the listener flow
                .takeWhile { it !is WalEvent.Message || it.content != "QUIT" }
                .collect { event -> event.prettyPrint() }
            println("--- WAL Listener stopped ---")
        }

        // 2. Start the listener
        listener.start(recreateSlot = false)

        // 3. Wait until the listener is ready
        readyFlag.awaitReady()

        // 4. Run example queries that trigger WAL events
        runExampleQueries(database)

        // 5. Send a special logical message to signal the listener to stop
        database.suspendTransaction {
            emitLogicalPgMessage(transactional = true, prefix = "info", message = "QUIT")
        }

        listenerJob.join()
        listener.stop()
    }
}

private fun setupWalListener(): PgenWalEventListener = pgenWalListener(
    slot = "runtime_example",
    url = "jdbc:postgresql://localhost:55422/postgres",
) {
    statusUpdateInterval(0.seconds) // acknowledged immediately
    addPgProperty(PGProperty.USER, "postgres")
    addPgProperty(PGProperty.PASSWORD, "postgres")
    addTable(BaseSchemaPublic.eventTables) // listen to all tables of schema 'public'
}

private fun setupDatabase(): R2dbcDatabase = r2dbcDatabasePooled {
    url("jdbc:postgresql://localhost:55422/postgres")
    username("postgres")
    password("postgres")
}

private suspend fun runExampleQueries(database: R2dbcDatabase) {
    println("--- Running example queries ---")

    val newUser = Users.CreateEntity(
        id = Option.Undefined,                  // DB default: gen_random_uuid()
        username = NonEmptyText("admin-${Clock.System.now().nanosecondsOfSecond}"),
        email = null,                           // store NULL explicitly
        displayName = "admin",
        roles = Option.Some(listOf("ADMIN")),   // provide explicit value
        preferences = Option.Undefined,         // omit column; keep DB default
        createdAt = Option.Undefined,           // DB default: now()
        lastLoginAt = null,                     // store NULL explicitly
    )

    database.suspendTransaction {
        println("Emitting logical message...")
        emitLogicalPgMessage(transactional = true, prefix = "info", message = "hello world")
    }
    val userId = database.suspendTransaction {
        println("Inserting new user...")
        Users.insertReturning {
            newUser applyTo it
        }.map { it[Users.id] }.single()
    }
    database.suspendTransaction {
        println("Updating user email...")
        Users.update(where = { Users.id eq userId }) {
            it[Users.email] = "admin@example.com"
        }
    }
    database.suspendTransaction {
        println("Deleting user...")
        Users.deleteWhere { Users.id eq userId }
    }
}

private fun WalEvent<*>.prettyPrint() {
    when (this) {
        is WalEvent.Message -> println("[MESSAGE] prefix=${prefix}, message=${content}")
        is WalEvent.Change<*> -> {
            val tableName = table.name
            when (val p = payload) {
                Payload.Truncate -> println("[$tableName] TRUNCATE Event")
                is Payload.Insert<*> -> println("[$tableName] INSERT Event: NEW=${p.dataNew.toPrettyString()}")
                is Payload.Delete<*> -> println("[$tableName] DELETE Event: OLD=${p.dataOld.toPrettyString()}")
                is Payload.Update<*> -> println(
                    "[$tableName] UPDATE Event: OLD=${p.dataNew.toPrettyString()} " +
                            "NEW=${p.dataNew.toPrettyString()}"
                )
            }
        }
    }
}

private fun Any.toPrettyString(): String = when (this) {
    is Users.EventEntity -> "User(id=$id, email=$email, displayName=$displayName, ...)"
    else -> toString()
}

private class ReadyFlag {
    private val ready = CompletableDeferred<Unit>()

    fun markReady() {
        ready.complete(Unit)
    }

    suspend fun awaitReady() {
        ready.await()
    }
}
