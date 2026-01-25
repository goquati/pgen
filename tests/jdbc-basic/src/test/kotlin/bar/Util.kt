package bar

import de.quati.kotlin.util.Option
import de.quati.kotlin.util.Result
import de.quati.kotlin.util.successOrNull
import de.quati.pgen.jdbc.util.transaction
import de.quati.pgen.jdbc.util.transactionCatching
import de.quati.pgen.shared.PgenException
import de.quati.pgen.tests.jdbc.basic.createDb
import de.quati.pgen.tests.jdbc.basic.generated.db.bar.public1.ItemEmbeddings
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.uuid.Uuid

internal val db = createDb("jdbc_basic_vector")

internal fun cleanUpAll(): Unit = db.transaction {
    ItemEmbeddings.deleteAll()
}

internal fun embeddingVector(dim: Int = 64): FloatArray =
    FloatArray(dim) { i -> i.toFloat() }

internal fun createItemEmbeddingFixture(
    title: String = "Title ${Uuid.random()}",
    description: String? = "A test embedding",
    embedding: FloatArray = embeddingVector(),
    body: ItemEmbeddings.(InsertStatement<Number>) -> Unit = {},
): Result<Long, PgenException> {
    val result = db.transactionCatching {
        ItemEmbeddings.insert { builder ->
            ItemEmbeddings.CreateEntity(
                id = Option.Undefined,             // bigserial, let DB assign
                itemUuid = Option.Undefined,       // default gen_random_uuid()
                title = title,
                description = description,
                embedding = embedding,
                metadata = Option.Some(
                    JsonObject(mapOf("foo" to JsonPrimitive("bar")))
                ),
                createdAt = Option.Undefined,      // default now()
            ).applyTo(builder)
            body(builder)
        }[ItemEmbeddings.id]
    }
    result.successOrNull?.also { it shouldNotBe null }
    return result
}