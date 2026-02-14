package foo.testtables

import de.quati.pgen.jdbc.util.transaction
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.Email
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.EnumArrayTestTable
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.OrderStatus
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.TextDomainArrayTestTable
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.UuidArrayTestTable
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.UuidDomainArrayTestTable
import de.quati.pgen.tests.jdbc.basic.shared.UserId
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.uuid.Uuid

class ArrayTest {
    @Test
    fun `enum array tests`() {
        val table = EnumArrayTestTable
        cleanUp(table)
        val d1 = listOf(OrderStatus.PENDING, OrderStatus.PAID)
        val d2 = listOf<OrderStatus>()
        val d3 = listOf(OrderStatus.CANCELLED)

        db.transaction {
            table.insert {
                it[table.key] = "foo"
                it[table.data] = d1
            }
            table.selectAll().where { table.key eq "foo" }.single()
                .let(EnumArrayTestTable.Entity::create)
        }.also { row ->
            row.data shouldBe d1
            row.dataNullable shouldBe null
        }
        db.transaction {
            table.insert {
                it[table.key] = "bar"
                it[table.data] = d3
                it[table.dataNullable] = d2
            }
            table.selectAll().where { table.key eq "bar" }.single()
                .let(EnumArrayTestTable.Entity::create)
        }.also { row ->
            row.data shouldBe d3
            row.dataNullable shouldBe d2
        }
    }

    @Test
    fun `uuid array tests`() {
        val table = UuidArrayTestTable
        cleanUp(table)
        val d1 = listOf(
            Uuid.parse("a75d59ec-7aca-41c7-9dc0-19ebf3391298"),
            Uuid.parse("1b985967-6bdc-420e-be1e-12b0ffea9926"),
        )
        val d2 = listOf<Uuid>()
        val d3 = listOf(Uuid.parse("55847eba-3ba1-4a18-b4af-4a9c49415272"))

        db.transaction {
            table.insert {
                it[table.key] = "foo"
                it[table.data] = d1
            }
            table.selectAll().where { table.key eq "foo" }.single()
                .let(UuidArrayTestTable.Entity::create)
        }.also { row ->
            row.data shouldBe d1
            row.dataNullable shouldBe null
        }
        db.transaction {
            table.insert {
                it[table.key] = "bar"
                it[table.data] = d3
                it[table.dataNullable] = d2
            }
            table.selectAll().where { table.key eq "bar" }.single()
                .let(UuidArrayTestTable.Entity::create)
        }.also { row ->
            row.data shouldBe d3
            row.dataNullable shouldBe d2
        }
    }

    @Test
    fun `domain text array tests`() {
        val table = TextDomainArrayTestTable
        cleanUp(table)
        val d1 = listOf(
            Email("foo@example.com"),
            Email("bar@example.com"),
        )
        val d2 = listOf<Email>()
        val d3 = listOf(Email("hello@example.com"))

        db.transaction {
            table.insert {
                it[table.key] = "foo"
                it[table.data] = d1
            }
            table.selectAll().where { table.key eq "foo" }.single()
                .let(TextDomainArrayTestTable.Entity::create)
        }.also { row ->
            row.data shouldBe d1
            row.dataNullable shouldBe null
        }
        db.transaction {
            table.insert {
                it[table.key] = "bar"
                it[table.data] = d3
                it[table.dataNullable] = d2
            }
            table.selectAll().where { table.key eq "bar" }.single()
                .let(TextDomainArrayTestTable.Entity::create)
        }.also { row ->
            row.data shouldBe d3
            row.dataNullable shouldBe d2
        }
    }

    @Test
    @Disabled // TODO not yet supported
    fun `domain uuid array tests`() {
        val table = UuidDomainArrayTestTable
        cleanUp(table)
        val d1 = listOf(
            UserId("a75d59ec-7aca-41c7-9dc0-19ebf3391298"),
            UserId("1b985967-6bdc-420e-be1e-12b0ffea9926"),
        )
        val d2 = listOf<UserId>()
        val d3 = listOf(UserId("55847eba-3ba1-4a18-b4af-4a9c49415272"))

        db.transaction {
            table.insert {
                it[table.key] = "foo"
                it[table.data] = d1
            }
            table.selectAll().where { table.key eq "foo" }.single()
                .let(UuidDomainArrayTestTable.Entity::create)
        }.also { row ->
            row.data shouldBe d1
            row.dataNullable shouldBe null
        }
        db.transaction {
            table.insert {
                it[table.key] = "bar"
                it[table.data] = d3
                it[table.dataNullable] = d2
            }
            table.selectAll().where { table.key eq "bar" }.single()
                .let(UuidDomainArrayTestTable.Entity::create)
        }.also { row ->
            row.data shouldBe d3
            row.dataNullable shouldBe d2
        }
    }
}