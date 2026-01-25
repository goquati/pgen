package bar

import de.quati.kotlin.util.Option
import de.quati.kotlin.util.Result
import de.quati.kotlin.util.getOrThrow
import de.quati.pgen.core.util.onCheckViolation
import de.quati.pgen.jdbc.util.deleteSingle
import de.quati.pgen.jdbc.util.transaction
import de.quati.pgen.shared.PgenException
import de.quati.pgen.tests.jdbc.basic.generated.db.bar.public1.ItemEmbeddings
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.uuid.Uuid
import kotlin.math.absoluteValue
import kotlin.test.BeforeTest
import kotlin.test.Test

class ItemEmbeddingsTest {
    @BeforeTest
    fun cleanUp() { cleanUpAll() }

    private fun count() = db.transaction(readOnly = true) {
        ItemEmbeddings.selectAll().count()
    }

    @Test
    fun `create item embedding and read it back using Entity`() {
        val expectedEmbedding = embeddingVector()
        val title = "Test title ${Uuid.random()}"
        val id = createItemEmbeddingFixture(
            title = title,
            embedding = expectedEmbedding,
        ).getOrThrow()

        val entity = db.transaction(readOnly = true) {
            ItemEmbeddings.selectAll()
                .where { ItemEmbeddings.id eq id }
                .single()
                .let(ItemEmbeddings.Entity::create)
        }

        entity.id shouldBe id
        entity.itemUuid shouldNotBe null
        entity.title shouldBe title
        entity.description shouldBe "A test embedding"
        entity.embedding.toList() shouldBe expectedEmbedding.toList()
        entity.metadata shouldBe JsonObject(mapOf("foo" to JsonPrimitive("bar")))
        entity.createdAt shouldNotBe null
    }

    @Test
    fun `create item embedding with defaults for item_uuid and created_at`() {
        val id = db.transaction {
            ItemEmbeddings.insert { builder ->
                ItemEmbeddings.CreateEntity(
                    id = Option.Undefined,
                    itemUuid = Option.Undefined, // DB default gen_random_uuid()
                    title = "Defaulted ${Uuid.random()}",
                    description = null,
                    embedding = embeddingVector(),
                    metadata = Option.Undefined, // DB default '{}'::jsonb
                    createdAt = Option.Undefined, // DB default now()
                ).applyTo(builder)
            }[ItemEmbeddings.id]
        }

        val entity = db.transaction(readOnly = true) {
            ItemEmbeddings.selectAll()
                .where { ItemEmbeddings.id eq id }
                .single()
                .let(ItemEmbeddings.Entity::create)
        }

        entity.itemUuid shouldNotBe null
        entity.metadata shouldNotBe null           // '{}'::jsonb
        entity.createdAt shouldNotBe null
    }

    @Test
    fun `update item embedding using UpdateEntity updates only specified columns`() {
        val initialEmbedding = embeddingVector()
        val id = createItemEmbeddingFixture(
            title = "Initial title",
            description = "Initial description",
            embedding = initialEmbedding,
        ).getOrThrow()

        val newEmbedding = FloatArray(64) { i -> (i * 2).toFloat() }
        val newCreatedAt = OffsetDateTime.now().minusDays(1) // should NOT be used

        db.transaction {
            ItemEmbeddings.update(
                where = { ItemEmbeddings.id eq id }
            ) { builder ->
                ItemEmbeddings.UpdateEntity(
                    id = Option.Undefined,
                    itemUuid = Option.Undefined,
                    title = Option.Some("Updated title"),
                    description = Option.Some(null),
                    embedding = Option.Some(newEmbedding),
                    metadata = Option.Some(
                        JsonObject(mapOf("updated" to JsonPrimitive(true)))
                    ),
                    createdAt = Option.Undefined,  // don't touch created_at
                ).applyTo(builder)
            }
        }

        val entity = db.transaction(readOnly = true) {
            ItemEmbeddings.selectAll()
                .where { ItemEmbeddings.id eq id }
                .single()
                .let(ItemEmbeddings.Entity::create)
        }

        entity.title shouldBe "Updated title"
        entity.description shouldBe null
        entity.embedding.toList() shouldBe newEmbedding.toList()
        entity.metadata shouldBe JsonObject(mapOf("updated" to JsonPrimitive(true)))
        // createdAt should still be set by DB, not overridden
        ChronoUnit.MILLIS.between(entity.createdAt, newCreatedAt).absoluteValue shouldBeGreaterThan 0L
    }

    @Test
    fun `delete item embedding removes row`() {
        val id1 = createItemEmbeddingFixture().getOrThrow()
        val id2 = createItemEmbeddingFixture().getOrThrow()

        count() shouldBe 2

        db.transaction {
            ItemEmbeddings.deleteWhere { ItemEmbeddings.id eq id1 }
        } shouldBe 1

        db.transaction {
            ItemEmbeddings.deleteSingle { ItemEmbeddings.id eq id2 }
        }.getOrThrow("")

        count() shouldBe 0
    }

    @Test
    fun `constraint error - title must not be empty or whitespace`() {
        fun Result<*, PgenException>.getFailedConstraint() = run {
            ItemEmbeddings.Constraints.itemEmbeddingsTitleNotEmpty.also { c ->
                onCheckViolation(c) { return@run c }
            }
            null
        }

        // title = "   " should violate item_embeddings_title_not_empty
        createItemEmbeddingFixture(title = "   ")
            .getFailedConstraint() shouldBe ItemEmbeddings.Constraints.itemEmbeddingsTitleNotEmpty

        count() shouldBe 0
    }
}
