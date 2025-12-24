package foo

import de.quati.kotlin.util.Option
import de.quati.kotlin.util.Result
import de.quati.kotlin.util.getOrThrow
import de.quati.pgen.core.util.onCheckViolation
import de.quati.pgen.core.util.onForeignKeyViolation
import de.quati.pgen.r2dbc.util.deleteSingle
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.Orders
import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.shared.PgenException
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.OrderStatus
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo.deleteByIdOrThrow
import de.quati.pgen.tests.r2dbc.basic.shared.UserId
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import java.util.UUID
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class OrdersTest {
    @BeforeTest
    fun cleanUp(): Unit = runBlocking { cleanUpAll() }

    private suspend fun count() = db.suspendTransaction(readOnly = true) {
        Orders.selectAll().count()
    }

    @Test
    fun `create order and read it back using Entity`(): Unit = runBlocking {
        val userId = createUserFixture().getOrThrow()
        val productId = createProductFixture().getOrThrow()
        val orderId = createOrderFixture(userId, productId).getOrThrow()
        val entity = db.suspendTransaction(readOnly = true) {
            Orders.selectAll()
                .where { Orders.id eq orderId }
                .single().let(Orders.Entity::create)
        }
        entity.id shouldBe orderId
        entity.userId shouldBe userId
        entity.productId shouldBe productId
        entity.status shouldBe OrderStatus.PAID
        entity.totalCents shouldBe 5000
        entity.notes shouldBe "Initial order"
        entity.placedAt shouldNotBe null
        entity.rawJsonPayload shouldBe JsonObject(mapOf("hello" to JsonPrimitive("world")))
        entity.tags shouldBe listOf("test", "integration")
    }

    @Test
    fun `update order using UpdateEntity updates only specified columns`(): Unit = runBlocking {
        val userId = createUserFixture().getOrThrow()
        val productId = createProductFixture().getOrThrow()
        val orderId = createOrderFixture(userId, productId).getOrThrow()
        val processedAt = Clock.System.now()
        db.suspendTransaction {
            Orders.update(
                where = { Orders.id eq orderId }
            ) { builder ->
                Orders.UpdateEntity(
                    id = Option.Undefined,
                    orderUuid = Option.Undefined,
                    userId = Option.Undefined,
                    productId = Option.Undefined,
                    status = Option.Some(OrderStatus.REFUNDED),
                    totalCents = Option.Some(2000),
                    notes = Option.Some("After update"),
                    placedAt = Option.Undefined,
                    processedAt = Option.Some(processedAt),
                    metadata = Option.Undefined,
                    rawJsonPayload = Option.Undefined,
                    receiptPdf = Option.Undefined,
                    tags = Option.Some(listOf("after", "updated")),
                ).applyTo(builder)
            }
        }

        val entity = db.suspendTransaction(readOnly = true) {
            Orders.selectAll()
                .where { Orders.id eq orderId }
                .single().let(Orders.Entity::create)
        }
        entity.status shouldBe OrderStatus.REFUNDED
        entity.totalCents shouldBe 2000
        entity.notes shouldBe "After update"
        entity.processedAt!!.minus(processedAt).absoluteValue shouldBeLessThan 1.seconds
        entity.tags shouldBe listOf("after", "updated")
    }

    @Test
    fun `delete order removes row`(): Unit = runBlocking {
        val userId = createUserFixture().getOrThrow()
        val productId = createProductFixture().getOrThrow()

        createOrderFixture(userId, productId).getOrThrow().also { orderId ->
            db.suspendTransaction { Orders.deleteWhere { Orders.id eq orderId } } shouldBe 1
        }
        createOrderFixture(userId, productId).getOrThrow().also { orderId ->
            db.suspendTransaction { Orders.deleteSingle { Orders.id eq orderId } }.getOrThrow()
        }
        createOrderFixture(userId, productId).getOrThrow().also { orderId ->
            db.suspendTransaction { Orders.deleteByIdOrThrow(orderId) }
        }
        count() shouldBe 0
    }


    @Test
    fun `constraint error`(): Unit = runBlocking {
        val userId = createUserFixture().getOrThrow()
        val productId = createProductFixture().getOrThrow()
        fun Result<*, PgenException>.getFailedConstraint() = run {
            Orders.Constraints.ordersUserFk.also { c -> onForeignKeyViolation(c) { return@run c } }
            Orders.Constraints.ordersProductFk.also { c -> onForeignKeyViolation(c) { return@run c } }
            Orders.Constraints.ordersTotalPositive.also { c -> onCheckViolation(c) { return@run c } }
            null
        }

        createOrderFixture(userId, -47)
            .getFailedConstraint() shouldBe Orders.Constraints.ordersProductFk
        createOrderFixture(UserId(UUID.randomUUID()), productId)
            .getFailedConstraint() shouldBe Orders.Constraints.ordersUserFk
        createOrderFixture(userId, productId) { it[Orders.totalCents] = -1 }
            .getFailedConstraint() shouldBe Orders.Constraints.ordersTotalPositive

        count() shouldBe 0
    }
}
