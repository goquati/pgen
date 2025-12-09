package foo

import de.quati.kotlin.util.Option
import de.quati.kotlin.util.Result
import de.quati.kotlin.util.getOrThrow
import de.quati.pgen.core.util.onCheckViolation
import de.quati.pgen.r2dbc.util.deleteSingle
import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.shared.PgenException
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.NonEmptyTextDomain
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.OrderStatus
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.ProductType
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.Products
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
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class ProductsTest {
    @BeforeTest
    fun cleanUp(): Unit = runBlocking { cleanUpAll() }

    private suspend fun count() = db.suspendTransaction(readOnly = true) {
        Products.selectAll().count()
    }

    @Test
    fun `create product and read it back using Entity`(): Unit = runBlocking {
        val sku = "SKU-${UUID.randomUUID()}"
        val productId = createProductFixture(sku).getOrThrow()

        val entity = db.suspendTransaction(readOnly = true) {
            Products.selectAll()
                .where { Products.id eq productId }
                .single()
                .let(Products.Entity::create)
        }

        entity.id shouldBe productId
        entity.sku shouldBe sku
        entity.name shouldBe NonEmptyTextDomain("Test Product")
        entity.type shouldBe ProductType.a
        entity.description.shouldBeNull()
        entity.priceCents shouldBe 1234
        entity.status shouldBe OrderStatus.pending
        entity.extraData shouldBe JsonObject(mapOf("bar" to JsonPrimitive("47")))
        entity.createdAt shouldNotBe null
    }

    @Test
    fun `create product with default status and createdAt`(): Unit = runBlocking {
        val sku = "SKU-${UUID.randomUUID()}"
        val productId = db.suspendTransaction {
            Products.insert { builder ->
                Products.CreateEntity(
                    id = Option.Undefined,
                    sku = sku,
                    name = NonEmptyTextDomain("Default Product"),
                    type = ProductType.b,
                    description = "Has defaults",
                    priceCents = 5000,
                    status = Option.Undefined,  // let DB default 'pending'
                    extraData = JsonObject(emptyMap()),
                    createdAt = Option.Undefined,
                ) applyTo builder
            }[Products.id]
        }

        val entity = db.suspendTransaction(readOnly = true) {
            Products.selectAll()
                .where { Products.id eq productId }
                .single()
                .let(Products.Entity::create)
        }

        entity.status shouldBe OrderStatus.pending
        entity.createdAt shouldNotBe null
    }

    @Test
    fun `update product using UpdateEntity updates only specified columns`(): Unit = runBlocking {
        val sku = "SKU-${UUID.randomUUID()}"
        val productId = createProductFixture(sku).getOrThrow()

        db.suspendTransaction {
            Products.update(
                where = { Products.id eq productId }
            ) { builder ->
                Products.UpdateEntity(
                    id = Option.Undefined,
                    sku = Option.Undefined,
                    name = Option.Some(NonEmptyTextDomain("Updated Product")),
                    type = Option.Some(ProductType.c),
                    description = Option.Some("Updated description"),
                    priceCents = Option.Some(9999),
                    status = Option.Some(OrderStatus.paid),
                    extraData = Option.Some(JsonObject(mapOf("updated" to JsonPrimitive(true)))),
                    createdAt = Option.Undefined,
                ) applyTo builder
            }
        }

        val entity = db.suspendTransaction(readOnly = true) {
            Products.selectAll()
                .where { Products.id eq productId }
                .single()
                .let(Products.Entity::create)
        }

        entity.name shouldBe NonEmptyTextDomain("Updated Product")
        entity.type shouldBe ProductType.c
        entity.description shouldBe "Updated description"
        entity.priceCents shouldBe 9999
        entity.status shouldBe OrderStatus.paid
        entity.extraData shouldBe JsonObject(mapOf("updated" to JsonPrimitive(true)))
    }

    @Test
    fun `delete product removes row`(): Unit = runBlocking {
        val id1 = createProductFixture("SKU1-${UUID.randomUUID()}").getOrThrow()
        val id2 = createProductFixture("SKU2-${UUID.randomUUID()}").getOrThrow()

        count() shouldBe 2

        db.suspendTransaction {
            Products.deleteWhere { Products.id eq id1 }
        } shouldBe 1

        db.suspendTransaction {
            Products.deleteSingle { Products.id eq id2 }
        }.getOrThrow("")

        count() shouldBe 0
    }

    @Test
    fun `constraint error - price must be positive`(): Unit = runBlocking {
        fun Result<*, PgenException>.getFailedConstraint() = run {
            Products.Constraints.productsPricePositive.also { c ->
                onCheckViolation(c) { return@run c }
            }
            null
        }

        createProductFixture {
            it[Products.priceCents] = -1
        }.getFailedConstraint() shouldBe Products.Constraints.productsPricePositive

        count() shouldBe 0
    }
}
