package bar

import de.quati.kotlin.util.Option
import de.quati.kotlin.util.Result
import de.quati.kotlin.util.successOrNull
import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.r2dbc.util.suspendTransactionCatching
import de.quati.pgen.shared.PgenException
import de.quati.pgen.tests.r2dbc.basic.createDb
import de.quati.pgen.tests.r2dbc.basic.generated.db.bar._public.ItemEmbeddings
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import java.util.UUID

internal val db = createDb("r2dbc_basic_vector")

internal suspend fun cleanUpAll(): Unit = db.suspendTransaction {
    ItemEmbeddings.deleteAll()
}

internal fun embeddingVector(dim: Int = 64): FloatArray =
    FloatArray(dim) { i -> i.toFloat() }

internal suspend fun createItemEmbeddingFixture(
    title: String = "Title ${UUID.randomUUID()}",
    description: String? = "A test embedding",
    embedding: FloatArray = embeddingVector(),
    body: ItemEmbeddings.(InsertStatement<Number>) -> Unit = {},
): Result<Long, PgenException> {
    val result = db.suspendTransactionCatching {
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