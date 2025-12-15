package foo

import de.quati.kotlin.util.Option
import de.quati.kotlin.util.Result
import de.quati.kotlin.util.successOrNull
import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.r2dbc.util.suspendTransactionCatching
import de.quati.pgen.shared.PgenException
import de.quati.pgen.tests.r2dbc.basic.createDb
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.Documents
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.NonEmptyTextDomain
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.OrderId
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.OrderStatus
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.Orders
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.PgenTestTable
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.ProductType
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.Products
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.SyncTestTable
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.Users
import de.quati.pgen.tests.r2dbc.basic.shared.UserId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insert
import java.util.UUID

internal val db = createDb(55430)

internal suspend fun cleanUpAll(): Unit = db.suspendTransaction {
    Documents.deleteAll()
    Orders.deleteAll()
    Products.deleteAll()
    Users.deleteAll()
    PgenTestTable.deleteAll()
    SyncTestTable.deleteAll()
}

internal suspend fun createUserFixture(
    username: String = "user-${UUID.randomUUID()}",
    body: Users.(InsertStatement<Number>) -> Unit = {},
): Result<UserId, PgenException> {
    val userIdResult = db.suspendTransactionCatching {
        Users.insert { builder ->
            Users.CreateEntity(
                id = Option.Undefined,
                username = NonEmptyTextDomain(username),
                email = "$username@example.com",
                displayName = null,
                roles = Option.Some(listOf("user")),
                preferences = Option.Some(JsonObject(mapOf("theme" to JsonPrimitive("dark")))),
                createdAt = Option.Undefined,
                lastLoginAt = null,
            ) applyTo builder
            body(builder)
        }[Users.id]
    }
    userIdResult.successOrNull?.also { it shouldNotBe null }
    return userIdResult
}

internal suspend fun createProductFixture(
    sku: String = "SKU-${UUID.randomUUID()}",
    body: Products.(InsertStatement<Number>) -> Unit = {},
): Result<Int, PgenException> {
    val productIdResult = db.suspendTransactionCatching {
        Products.insert { builder ->
            Products.CreateEntity(
                id = Option.Undefined,
                sku = sku,
                name = NonEmptyTextDomain("Test Product"),
                type = ProductType.a,
                description = null,
                priceCents = 1234,
                status = Option.Some(OrderStatus.pending),
                extraData = JsonObject(mapOf("bar" to JsonPrimitive("47"))),
                createdAt = Option.Undefined,
            ) applyTo builder
            body(builder)
        }[Products.id]
    }
    productIdResult.successOrNull?.also { it shouldNotBe null }
    return productIdResult
}

internal suspend fun createOrderFixture(
    userId: UserId,
    productId: Int,
    body: Orders.(InsertStatement<Number>) -> Unit = {},
): Result<OrderId, PgenException> {
    val orderId = OrderId(UUID.randomUUID())
    val createdOrderId = db.suspendTransactionCatching {
        Orders.insert { builder ->
            Orders.CreateEntity(
                id = orderId,
                orderUuid = Option.Undefined,
                userId = userId,
                productId = productId,
                status = Option.Some(OrderStatus.paid),
                totalCents = 5000,
                notes = "Initial order",
                placedAt = Option.Undefined,
                processedAt = null,
                metadata = null,
                rawJsonPayload = JsonObject(mapOf("hello" to JsonPrimitive("world"))),
                receiptPdf = null,
                tags = listOf("test", "integration"),
            ).applyTo(builder)
            body(builder)
        }[Orders.id]
    }
    createdOrderId.successOrNull?.also { it shouldBe orderId }
    return createdOrderId
}

internal suspend fun createDocumentFixture(
    ownerId: UserId,
    title: NonEmptyTextDomain? = NonEmptyTextDomain("Test Document"),
    body: Documents.(InsertStatement<Number>) -> Unit = {},
): Result<UUID, PgenException> {
    val result = db.suspendTransactionCatching {
        Documents.insert { builder ->
            Documents.CreateEntity(
                id = Option.Undefined,
                ownerId = ownerId,
                title = title,
                content = "Hello world".toByteArray(),
                contentType = Option.Undefined,
                metadata = Option.Some(
                    JsonObject(mapOf("foo" to JsonPrimitive("bar")))
                ),
                tags = Option.Some(listOf("test", "doc")),
                createdAt = Option.Undefined,
                updatedAt = null,
            ) applyTo builder
            body(builder)
        }[Documents.id]
    }
    result.successOrNull?.also { it shouldNotBe null }
    return result
}