package foo

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.map
import de.quati.pgen.tests.r2dbc.basic.shared.UserId
import de.quati.kotlin.util.Option
import de.quati.kotlin.util.Result
import de.quati.kotlin.util.getOrThrow
import de.quati.pgen.core.util.onCheckViolation
import de.quati.pgen.r2dbc.util.deleteSingle
import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.shared.PgenException
import de.quati.pgen.tests.r2dbc.basic.shared.PgName
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo.public.NonEmptyTextDomain
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo.public.Users
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.uuid.Uuid
import kotlin.math.absoluteValue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.toJavaInstant

class UsersTest {
    @BeforeTest
    fun cleanUp(): Unit = runBlocking { cleanUpAll() }

    private suspend fun count() = db.suspendTransaction(readOnly = true) {
        Users.selectAll().count()
    }

    @Test
    fun `create user and read it back using Entity`(): Unit = runBlocking {
        val username = "admin-${Uuid.random()}"
        val userId = createUserFixture(username).getOrThrow()

        val entity = db.suspendTransaction(readOnly = true) {
            Users.selectAll()
                .where { Users.id eq userId }
                .single()
                .let(Users.Entity::create)
        }

        entity.id shouldBe userId
        entity.username shouldBe NonEmptyTextDomain(username)
        entity.email shouldBe "$username@example.com"
        entity.name shouldBe PgName(username)
        entity.displayName.shouldBeNull()
        entity.roles shouldContainExactly listOf("user")
        entity.preferences shouldBe JsonObject(mapOf("theme" to JsonPrimitive("dark")))
        entity.createdAt shouldNotBe null
        entity.lastLoginAt shouldBe null
    }

    @Test
    fun `create user with default roles and preferences`(): Unit = runBlocking {
        val username = "default-${Uuid.random()}"
        val userId = db.suspendTransaction {
            Users.insert { builder ->
                Users.CreateEntity(
                    id = Option.Undefined,
                    username = NonEmptyTextDomain(username),
                    email = "$username@example.com",
                    name = null,
                    displayName = "Display $username",
                    roles = Option.Undefined,
                    preferences = Option.Undefined,
                    createdAt = Option.Undefined,
                    lastLoginAt = null,
                ) applyTo builder
            }[Users.id]
        }

        val entity = db.suspendTransaction(readOnly = true) {
            Users.selectAll()
                .where { Users.id eq userId }
                .single()
                .let(Users.Entity::create)
        }

        entity.roles shouldNotBe emptyList<String>()
        entity.preferences shouldNotBe null
    }

    @Test
    fun `update user using UpdateEntity updates only specified columns`(): Unit = runBlocking {
        val username = "update-${Uuid.random()}"
        val userId = createUserFixture(username).getOrThrow()
        val now = Clock.System.now().toJavaInstant().atOffset(ZoneOffset.UTC)

        db.suspendTransaction {
            Users.update(
                where = { Users.id eq userId }
            ) { builder ->
                Users.UpdateEntity(
                    id = Option.Undefined,
                    username = Option.Undefined,
                    email = Option.Some("updated@example.com"),
                    name = Option.Undefined,
                    displayName = Option.Some("Updated User"),
                    roles = Option.Some(listOf("admin", "user")),
                    preferences = Option.Some(JsonObject(mapOf("theme" to JsonPrimitive("light")))),
                    createdAt = Option.Undefined,
                    lastLoginAt = Option.Some(now),
                ) applyTo builder
            }
        }

        val entity = db.suspendTransaction(readOnly = true) {
            Users.selectAll()
                .where { Users.id eq userId }
                .single()
                .let(Users.Entity::create)
        }

        entity.email shouldBe "updated@example.com"
        entity.displayName shouldBe "Updated User"
        entity.roles shouldContainExactly listOf("admin", "user")
        entity.preferences shouldBe JsonObject(mapOf("theme" to JsonPrimitive("light")))
        ChronoUnit.MILLIS.between(entity.lastLoginAt, now).absoluteValue shouldBe 0L
    }

    @Test
    fun `delete user removes row`(): Unit = runBlocking {
        val username1 = "delete1-${Uuid.random()}"
        val username2 = "delete2-${Uuid.random()}"

        val id1 = createUserFixture(username1).getOrThrow()
        val id2 = createUserFixture(username2).getOrThrow()

        count() shouldBe 2

        db.suspendTransaction {
            Users.deleteWhere { Users.id eq id1 }
        } shouldBe 1

        db.suspendTransaction {
            Users.deleteSingle { Users.id eq id2 }
        }.getOrThrow()

        count() shouldBe 0
    }

    @Test
    fun `constraint error - roles must not be empty`(): Unit = runBlocking {
        fun Result<*, PgenException>.getFailedConstraint() = run {
            Users.Constraints.usersRolesNotEmpty.also { c ->
                onCheckViolation(c) { return@run c }
            }
            null
        }

        createUserFixture("bad-roles") {
            it[Users.roles] = emptyList()
        }.getFailedConstraint() shouldBe Users.Constraints.usersRolesNotEmpty

        count() shouldBe 0
    }

    @Test
    fun `pg_catalog name column mapped via typeMappings`(): Unit = runBlocking {
        val special = PgName("Grüße 'quoted' \"x\" ; --")
        val id1 = createUserFixture("name-1") { it[Users.name] = special }.getOrThrow()
        val id2 = createUserFixture("name-2") { it[Users.name] = null }.getOrThrow()
        val id3 = createUserFixture("name-3") { it[Users.name] = PgName("a".repeat(70)) }.getOrThrow()

        suspend fun readName(id: UserId) = db.suspendTransaction(readOnly = true) {
            Users.selectAll().where { Users.id eq id }.single()[Users.name]
        }
        readName(id1) shouldBe special
        readName(id2).shouldBeNull()
        readName(id3) shouldBe PgName("a".repeat(63)) // postgres truncates 'name' to 63 bytes

        db.suspendTransaction(readOnly = true) {
            Users.selectAll().where { Users.name eq special }.map { it[Users.id] }.toList()
        } shouldContainExactly listOf(id1)

        db.suspendTransaction { Users.update({ Users.id eq id2 }) { it[Users.name] = PgName("pg_class") } }
        readName(id2) shouldBe PgName("pg_class")

        db.suspendTransaction(readOnly = true) {
            exec("select name::text, pg_typeof(name)::text from public.users where id = '${id2.value}'") { rs ->
                rs.get(0, String::class.java) to rs.get(1, String::class.java)
            }!!.single()
        } shouldBe ("pg_class" to "name")
    }
}
