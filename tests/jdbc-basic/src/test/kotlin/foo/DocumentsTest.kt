package foo

import de.quati.kotlin.util.Option
import de.quati.kotlin.util.Result
import de.quati.kotlin.util.getOrThrow
import de.quati.pgen.core.util.onCheckViolation
import de.quati.pgen.core.util.onForeignKeyViolation
import de.quati.pgen.jdbc.util.deleteSingle
import de.quati.pgen.jdbc.util.transaction
import de.quati.pgen.shared.PgenException
import de.quati.pgen.tests.jdbc.basic.generated.db.foo._public.Documents
import de.quati.pgen.tests.jdbc.basic.generated.db.foo._public.NonEmptyTextDomain
import de.quati.pgen.tests.jdbc.basic.shared.UserId
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.absoluteValue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaInstant

class DocumentsTest {
    @BeforeTest
    fun cleanUp() { cleanUpAll() }

    private fun count() = db.transaction(readOnly = true) {
        Documents.selectAll().count()
    }

    @Test
    fun `create document and read it back using Entity`() {
        val ownerId = createUserFixture().getOrThrow()
        val docId = createDocumentFixture(ownerId).getOrThrow()

        val entity = db.transaction(readOnly = true) {
            Documents.selectAll()
                .where { Documents.id eq docId }
                .single()
                .let(Documents.Entity::create)
        }

        entity.id shouldBe docId
        entity.ownerId shouldBe ownerId
        entity.title shouldBe NonEmptyTextDomain("Test Document")
        entity.content?.decodeToString() shouldBe "Hello world"
        entity.contentType shouldBe "application/octet-stream"
        entity.metadata shouldBe JsonObject(mapOf("foo" to JsonPrimitive("bar")))
        entity.tags shouldContainExactly listOf("test", "doc")
        entity.createdAt shouldNotBe null
        entity.updatedAt.shouldBeNull()
    }

    @Test
    fun `update document using UpdateEntity updates only specified columns`() {
        val ownerId = createUserFixture().getOrThrow()
        val docId = createDocumentFixture(ownerId).getOrThrow()
        val updatedAt = Clock.System.now().toJavaInstant().atOffset(ZoneOffset.UTC)

        db.transaction {
            Documents.update(
                where = { Documents.id eq docId }
            ) { builder ->
                Documents.UpdateEntity(
                    id = Option.Undefined,
                    ownerId = Option.Undefined,
                    title = Option.Some(NonEmptyTextDomain("Updated Title")),
                    content = Option.Some("Updated content".toByteArray()),
                    contentType = Option.Some("text/plain"),
                    metadata = Option.Some(
                        JsonObject(mapOf("updated" to JsonPrimitive(true)))
                    ),
                    tags = Option.Some(listOf("updated", "doc")),
                    createdAt = Option.Undefined,
                    updatedAt = Option.Some(updatedAt),
                ) applyTo builder
            }
        }

        val entity = db.transaction(readOnly = true) {
            Documents.selectAll()
                .where { Documents.id eq docId }
                .single()
                .let(Documents.Entity::create)
        }

        entity.title shouldBe NonEmptyTextDomain("Updated Title")
        entity.content?.decodeToString() shouldBe "Updated content"
        entity.contentType shouldBe "text/plain"
        entity.metadata shouldBe JsonObject(mapOf("updated" to JsonPrimitive(true)))
        entity.tags shouldContainExactly listOf("updated", "doc")
        ChronoUnit.MILLIS.between(entity.updatedAt, updatedAt).absoluteValue shouldBe 0L
    }

    @Test
    fun `delete document removes row`() {
        val ownerId = createUserFixture().getOrThrow()
        val id1 = createDocumentFixture(ownerId).getOrThrow()
        db.transaction {
            Documents.deleteWhere { Documents.id eq id1 }
        } shouldBe 1

        val id2 = createDocumentFixture(ownerId).getOrThrow()
        db.transaction {
            Documents.deleteSingle { Documents.id eq id2 }
        }.getOrThrow("")

        count() shouldBe 0
    }

    @Test
    fun `constraint error - foreign key and updated_after_created`() {
        fun Result<*, PgenException>.getFailedConstraint() = run {
            Documents.Constraints.documentsOwnerIdFkey.also { c ->
                onForeignKeyViolation(c) { return@run c }
            }
            Documents.Constraints.documentsUpdatedAfterCreated.also { c ->
                onCheckViolation(c) { return@run c }
            }
            null
        }

        createDocumentFixture(
            ownerId = UserId(UUID.randomUUID())
        ).getFailedConstraint() shouldBe Documents.Constraints.documentsOwnerIdFkey

        val ownerId = createUserFixture().getOrThrow()
        createDocumentFixture(ownerId) { builder ->
            val created = Clock.System.now()
            val updated = created.minus(1.minutes)
            builder[Documents.createdAt] = created.toJavaInstant().atOffset(ZoneOffset.UTC)
            builder[Documents.updatedAt] = updated.toJavaInstant().atOffset(ZoneOffset.UTC)
        }.getFailedConstraint() shouldBe Documents.Constraints.documentsUpdatedAfterCreated

        count() shouldBe 0
    }
}
