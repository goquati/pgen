package foo

import de.quati.kotlin.util.Option
import de.quati.kotlin.util.getOrThrow
import de.quati.pgen.core.util.DeleteSingleResult
import de.quati.pgen.core.util.IsInsert
import de.quati.pgen.core.util.IsUpdate
import de.quati.pgen.core.util.UpdateSingleResult
import de.quati.pgen.core.util.arrayAgg
import de.quati.pgen.core.util.arrayContains
import de.quati.pgen.core.util.compoundAnd
import de.quati.pgen.core.util.compoundOr
import de.quati.pgen.core.util.iLike
import de.quati.pgen.core.util.like
import de.quati.pgen.jdbc.util.andWhere
import de.quati.pgen.jdbc.util.batchUpdate
import de.quati.pgen.jdbc.util.deleteSingle
import de.quati.pgen.jdbc.util.orWhere
import de.quati.pgen.jdbc.util.setLocalConfig
import de.quati.pgen.jdbc.util.transaction
import de.quati.pgen.jdbc.util.transactionWithContext
import de.quati.pgen.jdbc.util.updateSingle
import de.quati.pgen.shared.LocalConfigContext
import de.quati.pgen.tests.jdbc.basic.generated.db.foo._public.NonEmptyTextDomain
import de.quati.pgen.tests.jdbc.basic.generated.db.foo._public.SyncTestTable
import de.quati.pgen.tests.jdbc.basic.generated.db.foo._public.Users
import de.quati.pgen.tests.jdbc.basic.shared.UserId
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsertReturning
import java.util.UUID
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.test.BeforeTest
import kotlin.test.Test

class UtilsTest {
    @BeforeTest
    fun cleanUp() {
        cleanUpAll()
    }

    @Test
    fun `test like for StringLike and iLike`() {
        val usernameAdmin = "admin-${UUID.randomUUID()}"
        val usernameUser = "user-${UUID.randomUUID()}"
        val adminId = createUserFixture(usernameAdmin) { it[Users.displayName] = "foobar" }.getOrThrow()
        val userId = createUserFixture(usernameUser) { it[Users.displayName] = "hello" }.getOrThrow()

        db.transaction(readOnly = true) {
            Users.selectAll().count() shouldBe 2

            Users.selectAll()
                .andWhere(Users.username like "admin-%") // username is of type StringLike
                .single()[Users.id] shouldBe adminId
            Users.selectAll()
                .andWhere(Users.username like "Admin-%") // username is of type StringLike
                .count() shouldBe 0

            Users.selectAll()
                .andWhere(Users.username iLike "admin-%") // username is of type StringLike
                .single()[Users.id] shouldBe adminId
            Users.selectAll()
                .andWhere(Users.username iLike "Admin-%") // username is of type StringLike
                .single()[Users.id] shouldBe adminId
            Users.selectAll()
                .andWhere(Users.username iLike "USER-%") // username is of type StringLike
                .single()[Users.id] shouldBe userId
            Users.selectAll()
                .andWhere(Users.displayName iLike "%bar") // displayName is of type String
                .single()[Users.id] shouldBe adminId
            Users.selectAll()
                .andWhere(Users.displayName iLike "%Bar") // displayName is of type String
                .single()[Users.id] shouldBe adminId
        }
    }


    @Test
    fun `test isUpdate and isInsert`() {
        val userId = createUserFixture("admin") { it[Users.displayName] = "foobar" }.getOrThrow()
        val upsertData = Users.CreateEntity(
            id = Option.Some(userId),
            username = NonEmptyTextDomain("admin"),
            email = "admin@example.com",
            displayName = null,
            roles = Option.Some(listOf("user")),
            preferences = Option.Some(JsonObject(mapOf("theme" to JsonPrimitive("dark")))),
            createdAt = Option.Undefined,
            lastLoginAt = null,
        )

        db.transaction {
            Users.selectAll().count() shouldBe 1

            Users.upsertReturning(
                keys = arrayOf(Users.id),
                returning = listOf(IsInsert, IsUpdate)
            ) {
                upsertData applyTo it
            }.single().also {
                it[IsInsert] shouldBe false
                it[IsUpdate] shouldBe true
            }

            Users.selectAll().count() shouldBe 1

            Users.upsertReturning(
                keys = arrayOf(Users.id),
                returning = listOf(IsInsert, IsUpdate)
            ) {
                upsertData.copy(
                    id = Option.Some(UserId(UUID.randomUUID())),
                    username = NonEmptyTextDomain("new-admin"),
                    email = "new.admin@example.com",
                ) applyTo it
            }.single().also {
                it[IsInsert] shouldBe true
                it[IsUpdate] shouldBe false
            }
            Users.selectAll().count() shouldBe 2
        }
    }

    @Test
    fun `delete single`() {
        createUserFixture("admin1")
        createUserFixture("admin2")

        db.transaction {
            Users.selectAll().count() shouldBe 2
            Users.deleteSingle { Users.username like "other%" } shouldBe DeleteSingleResult.None
            Users.deleteSingle { Users.username like "admin%" } shouldBe DeleteSingleResult.TooMany
            Users.selectAll().count() shouldBe 2
            Users.deleteSingle { Users.username like "admin2" } shouldBe DeleteSingleResult.Success
            Users.selectAll().count() shouldBe 1
        }
    }

    @Test
    fun `update single`() {
        createUserFixture("admin1") { it[Users.displayName] = "foobar" }
        createUserFixture("admin2") { it[Users.displayName] = "foobar" }

        db.transaction {
            Users.selectAll().map { it[Users.displayName] }.toList() shouldBe listOf("foobar", "foobar")

            Users.updateSingle(where = { Users.username like "other%" }) {
                it[Users.displayName] = "hello"
            } shouldBe UpdateSingleResult.None
            Users.updateSingle(where = { Users.username like "admin%" }) {
                it[Users.displayName] = "hello"
            } shouldBe UpdateSingleResult.TooMany

            Users.selectAll().map { it[Users.displayName] }.toList() shouldBe listOf("foobar", "foobar")

            Users.updateSingle(where = { Users.username like "admin2" }) {
                it[Users.displayName] = "hello"
            }.let { it as? UpdateSingleResult.Success } shouldNotBe null

            Users.selectAll().map { it[Users.displayName] }.toSet() shouldBe setOf("hello", "foobar")
        }
    }

    @Test
    fun `test arrayContains`() {
        createUserFixture("no-admin")
        createUserFixture("admin-only") {
            it[Users.roles] = listOf("admin")
        }
        createUserFixture("admin-and-user") {
            it[Users.roles] = listOf("user", "admin")
        }
        val emailsWithAdminRole = db.transaction(readOnly = true) {
            Users.selectAll()
                .where { Users.roles arrayContains "admin" }
                .map { it[Users.email] }
                .toList()
        }
        emailsWithAdminRole shouldContainExactlyInAnyOrder listOf(
            "admin-and-user@example.com",
            "admin-only@example.com",
        )
    }

    @Test
    fun `test arrayAgg`() {
        createUserFixture("alice") { it[Users.displayName] = "foobar" }
        createUserFixture("bob") { it[Users.displayName] = "foobar" }
        createUserFixture("carol") { it[Users.displayName] = "hello" }

        val aggExpr = arrayAgg(Users.email)
        val result = db.transaction(readOnly = true) {
            Users.select(Users.displayName, aggExpr)
                .groupBy(Users.displayName)
                .toList()
        }.associate { it[Users.displayName] to it[aggExpr].toSet() }

        result shouldBe mapOf(
            "foobar" to setOf("alice@example.com", "bob@example.com"),
            "hello" to setOf("carol@example.com"),
        )
    }

    @Test
    fun `test compoundAnd, andWhere, compoundOr and orWhere`() {
        createUserFixture("alice")
        createUserFixture("bob")
        createUserFixture("carol")

        // --- compoundAnd with nulls should ignore nulls ----------------------
        db.transaction(readOnly = true) {
            val op = compoundAnd(
                Users.username eq NonEmptyTextDomain("alice"),
                null,
                Users.email eq "alice@example.com",
            )
            Users.selectAll()
                .where { op }
                .map { it[Users.email] }
                .toList()
        } shouldContainExactlyInAnyOrder listOf("alice@example.com")

        // --- compoundOr with nulls should ignore nulls -----------------------
        db.transaction(readOnly = true) {
            val op = compoundOr(
                Users.username eq NonEmptyTextDomain("alice"),
                null,
                Users.username eq NonEmptyTextDomain("bob"),
            )
            Users.selectAll()
                .where { op }
                .map { it[Users.email] }
                .toList()
        } shouldContainExactlyInAnyOrder listOf(
            "alice@example.com",
            "bob@example.com",
        )

        // --- andWhere should behave similarly ----------------------
        db.transaction(readOnly = true) {
            Users.selectAll()
                .andWhere(
                    Users.username eq NonEmptyTextDomain("alice"),
                    null,
                    Users.email eq "alice@example.com"
                ).map { it[Users.email] }.toList()
        } shouldContainExactlyInAnyOrder listOf("alice@example.com")

        // --- orWhere should behave similarly ----------------------
        db.transaction(readOnly = true) {
            Users.selectAll()
                .orWhere(
                    Users.username eq NonEmptyTextDomain("alice"),
                    null,
                    Users.username eq NonEmptyTextDomain("bob"),
                ).map { it[Users.email] }.toList()
        } shouldContainExactlyInAnyOrder listOf(
            "alice@example.com",
            "bob@example.com",
        )
    }

    @Test
    fun `test setLocalConfig`() {
        fun JdbcTransaction.getConfig(name: String) = exec("show $name") { rs ->
            buildList {
                while (rs.next())
                    add(rs.getString(1))
            }
        }!!.single()

        // Use a built-in GUC that we can SHOW afterwards, e.g. application_name
        db.transaction {
            setLocalConfig("application_name", "pgen-single")
            val current = getConfig("application_name")
            current shouldBe "pgen-single"
        }

        // Multiple settings at once via map overload
        db.transaction {
            setLocalConfig(
                mapOf(
                    "application_name" to "pgen-map",
                    "TimeZone" to "UTC",
                )
            )
            val appName = getConfig("application_name")
            val timeZone = getConfig("timezone")
            appName shouldBe "pgen-map"
            timeZone shouldBe "UTC"
        }

        with(LocalConfigContext(mapOf("application_name" to "foobar"))) {
            db.transactionWithContext {
                val current = getConfig("application_name")
                current shouldBe "foobar"
            }
        }

        // Because it's SET LOCAL, outside those transactions we should not see "pgen-map"
        db.transaction(readOnly = true) {
            val current = getConfig("application_name")
            current shouldNotBe "pgen-map"
        }
    }

    @Test
    fun `test transaction utils`() {
        db.transaction {
            createUserFixture("alice")
            createUserFixture("bob")
        }
        shouldThrowAny {
            db.transaction(readOnly = true) {
                createUserFixture("carol").getOrThrow()
            }
        }
        db.transaction(readOnly = true) { Users.selectAll().count() } shouldBe 2
    }

    @Test
    fun `test batchUpdate`() {
        val d1 = mapOf("foo" to 1, "bar" to 2, "baz" to 3, "qux" to 4)
        val d2 = mapOf("foo" to 10, "bar" to 2, "baz" to 30, "qux" to 40)
        db.transaction {
            SyncTestTable.batchInsert(d1.entries) { (k, v) ->
                this[SyncTestTable.name] = k
                this[SyncTestTable.groupId] = v
            }
            SyncTestTable.selectAll().associate { it[SyncTestTable.name] to it[SyncTestTable.groupId] }
        } shouldBe d1

        db.transaction {
            SyncTestTable.batchUpdate(
                keys = listOf(SyncTestTable.name),
                data = d2.entries.filter { it.key != "bar" },
            ) {
                this[SyncTestTable.name] = it.key
                this[SyncTestTable.groupId] = it.value
            }
            SyncTestTable.selectAll().associate { it[SyncTestTable.name] to it[SyncTestTable.groupId] }
        } shouldBe d2
    }
}
