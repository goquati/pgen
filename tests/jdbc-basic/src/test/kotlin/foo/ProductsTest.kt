package foo

import de.quati.kotlin.util.Option
import de.quati.kotlin.util.Result
import de.quati.kotlin.util.getOrThrow
import de.quati.pgen.core.util.onCheckViolation
import de.quati.pgen.jdbc.util.deleteSingle
import de.quati.pgen.jdbc.util.transaction
import de.quati.pgen.shared.PgenException
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.NonEmptyTextDomain
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.OrderStatus
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.ProductType
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.Products
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid
import kotlin.test.BeforeTest
import kotlin.test.Test

class ProductsTest {
    @BeforeTest
    fun cleanUp() {
        cleanUpAll()
    }

    private fun count() = db.transaction(readOnly = true) {
        Products.selectAll().count()
    }

    @Test
    fun `create product and read it back using Entity`() {
        val sku = "SKU-${Uuid.random()}"
        val productId = createProductFixture(sku).getOrThrow()

        val entity = db.transaction(readOnly = true) {
            Products.selectAll()
                .where { Products.id eq productId }
                .single()
                .let(Products.Entity::create)
        }

        entity.id shouldBe productId
        entity.sku shouldBe sku
        entity.name shouldBe NonEmptyTextDomain("Test Product")
        entity.type shouldBe ProductType.A
        entity.description.shouldBeNull()
        entity.priceCents shouldBe 1234
        entity.status shouldBe OrderStatus.PENDING
        entity.extraData shouldBe JsonObject(mapOf("bar" to JsonPrimitive("47")))
        entity.createdAt shouldNotBe null
    }

    @Test
    fun `create product with default status and createdAt`() {
        val sku = "SKU-${Uuid.random()}"
        val productId = db.transaction {
            Products.insert { builder ->
                Products.CreateEntity(
                    id = Option.Undefined,
                    sku = sku,
                    name = NonEmptyTextDomain("Default Product"),
                    type = ProductType.B,
                    description = "Has defaults",
                    priceCents = 5000,
                    status = Option.Undefined,  // let DB default 'pending'
                    extraData = JsonObject(emptyMap()),
                    createdAt = Option.Undefined,
                ) applyTo builder
            }[Products.id]
        }

        val entity = db.transaction(readOnly = true) {
            Products.selectAll()
                .where { Products.id eq productId }
                .single()
                .let(Products.Entity::create)
        }

        entity.status shouldBe OrderStatus.PENDING
        entity.createdAt shouldNotBe null
    }

    @Test
    fun `update product using UpdateEntity updates only specified columns`() {
        val sku = "SKU-${Uuid.random()}"
        val productId = createProductFixture(sku).getOrThrow()

        db.transaction {
            Products.update(
                where = { Products.id eq productId }
            ) { builder ->
                Products.UpdateEntity(
                    id = Option.Undefined,
                    sku = Option.Undefined,
                    name = Option.Some(NonEmptyTextDomain("Updated Product")),
                    type = Option.Some(ProductType.C),
                    description = Option.Some("Updated description"),
                    priceCents = Option.Some(9999),
                    status = Option.Some(OrderStatus.PAID),
                    extraData = Option.Some(JsonObject(mapOf("updated" to JsonPrimitive(true)))),
                    createdAt = Option.Undefined,
                ) applyTo builder
            }
        }

        val entity = db.transaction(readOnly = true) {
            Products.selectAll()
                .where { Products.id eq productId }
                .single()
                .let(Products.Entity::create)
        }

        entity.name shouldBe NonEmptyTextDomain("Updated Product")
        entity.type shouldBe ProductType.C
        entity.description shouldBe "Updated description"
        entity.priceCents shouldBe 9999
        entity.status shouldBe OrderStatus.PAID
        entity.extraData shouldBe JsonObject(mapOf("updated" to JsonPrimitive(true)))
    }

    @Test
    fun `delete product removes row`() {
        val id1 = createProductFixture("SKU1-${Uuid.random()}").getOrThrow()
        val id2 = createProductFixture("SKU2-${Uuid.random()}").getOrThrow()

        count() shouldBe 2

        db.transaction {
            Products.deleteWhere { Products.id eq id1 }
        } shouldBe 1

        db.transaction {
            Products.deleteSingle { Products.id eq id2 }
        }.getOrThrow("")

        count() shouldBe 0
    }

    @Test
    fun `constraint error - price must be positive`() {
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
