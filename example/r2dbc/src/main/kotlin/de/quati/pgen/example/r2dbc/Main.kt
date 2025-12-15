package de.quati.pgen.example.r2dbc

import de.quati.kotlin.util.Option
import de.quati.pgen.example.r2dbc.generated.db.base._public.NonEmptyText
import de.quati.pgen.example.r2dbc.generated.db.base._public.Users
import de.quati.pgen.r2dbc.util.r2dbcDatabasePooled
import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.r2dbc.util.transactionFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import java.util.UUID
import kotlin.time.Clock

// A tiny domain wrapper type to show how value classes work nicely with generated code
@JvmInline
value class UserId(val value: UUID)

suspend fun main() {
    // Create a pooled R2DBC database handle (you can also use R2dbcDatabase.connect(...)).
    // For a quick demo, we keep credentials inline. See README for docker-compose details.
    val database: R2dbcDatabase = r2dbcDatabasePooled {
        url("jdbc:postgresql://localhost:55420/postgres")
        username("postgres")
        password("postgres")
    }

    // Prepare a new user. The generator uses `Option` to differentiate between
    // "omit/use DB default" (Option.Undefined) and "set explicit null" (null) for nullable columns.
    // - Option.Undefined  -> column is omitted in INSERT; DB default (if any) applies
    // - null              -> column will be written as SQL NULL
    val newUser = Users.CreateEntity(
        id = Option.Undefined,                  // DB default: gen_random_uuid()
        username = NonEmptyText("admin-${Clock.System.now()}"),
        email = null,                           // store NULL explicitly
        displayName = "Admin",
        roles = Option.Some(listOf("ADMIN")),  // provide explicit value
        preferences = Option.Undefined,         // omit column; keep DB default
        createdAt = Option.Undefined,           // DB default: now()
        lastLoginAt = null,                     // store NULL explicitly
    )

    // A write transaction: insert the row. We can still override fields in the DSL.
    database.suspendTransaction {
        Users.insert {
            newUser applyTo it
            it[Users.displayName] = "admin" // override CreateEntity.displayName
        }
    }

    // A read-only transactional Flow. The block runs in a transaction, but the
    // returned Flow defers a row collection until downstream `collect`. That's OK:
    // `transactionFlow` keeps the transaction open while the Flow is consumed.
    val allUsersFlow: Flow<Users.Entity> = database.transactionFlow(readOnly = true) {
        Users.selectAll()
            .map(Users.Entity::create) // map ResultRow -> Users.Entity
    }

    println("All users in the database:")
    allUsersFlow.collect { user ->
        println("  • username=${user.username}, id=${user.id.value}")
    }
}