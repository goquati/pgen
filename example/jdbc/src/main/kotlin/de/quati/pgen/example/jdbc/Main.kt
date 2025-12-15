package de.quati.pgen.example.jdbc

import de.quati.kotlin.util.Option
import de.quati.pgen.example.jdbc.generated.db.base._public.NonEmptyText
import de.quati.pgen.example.jdbc.generated.db.base._public.Users
import de.quati.pgen.jdbc.util.transaction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.time.Clock

// A tiny domain wrapper type to show how value classes work nicely with generated code
@JvmInline
value class UserId(val value: UUID)

fun main() {
    // Create a Database.
    // For a quick demo, we keep credentials inline. See README for docker-compose details.
    val database: Database = Database.connect(
        url = "jdbc:postgresql://localhost:55421/postgres",
        user = "postgres",
        password = "postgres",
    )

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
    database.transaction {
        Users.insert {
            newUser applyTo it
            it[Users.displayName] = "admin" // override CreateEntity.displayName
        }
    }

    // A read-only transaction.
    val allUsersFlow: List<Users.Entity> = database.transaction(readOnly = true) {
        Users.selectAll()
            .map(Users.Entity::create) // map ResultRow -> Users.Entity
    }

    println("All users in the database:")
    allUsersFlow.forEach { user ->
        println("  • username=${user.username}, id=${user.id.value}")
    }
}