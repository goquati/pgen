package foo.testtables

import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo.public1.EnumArrayTestTable
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo.public1.OrderStatus
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo.public1.UuidArrayTestTable
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import kotlin.test.Test
import kotlin.uuid.Uuid

class ArrayTest {
    @Test
    fun `enum array tests`(): Unit = runBlocking {
        cleanUp(EnumArrayTestTable)
        val d1 = listOf(OrderStatus.PENDING, OrderStatus.PAID)
        val d2 = listOf<OrderStatus>()
        val d3 = listOf(OrderStatus.CANCELLED)

        db.suspendTransaction {
            EnumArrayTestTable.insert {
                it[EnumArrayTestTable.key] = "foo"
                it[EnumArrayTestTable.data] = d1
            }
            EnumArrayTestTable.selectAll().where { EnumArrayTestTable.key eq "foo" }.single()
        }.also { row ->
            row[EnumArrayTestTable.data] shouldBe d1
            row[EnumArrayTestTable.dataNullable] shouldBe null
        }
        db.suspendTransaction {
            EnumArrayTestTable.insert {
                it[EnumArrayTestTable.key] = "bar"
                it[EnumArrayTestTable.data] = d3
                it[EnumArrayTestTable.dataNullable] = d2
            }
            EnumArrayTestTable.selectAll().where { EnumArrayTestTable.key eq "bar" }.single()
        }.also { row ->
            row[EnumArrayTestTable.data] shouldBe d3
            row[EnumArrayTestTable.dataNullable] shouldBe d2
        }
    }

    @Test
    fun `uuid array tests`(): Unit = runBlocking {
        cleanUp(UuidArrayTestTable)
        val d1 = listOf(
            Uuid.parse("a75d59ec-7aca-41c7-9dc0-19ebf3391298"),
            Uuid.parse("1b985967-6bdc-420e-be1e-12b0ffea9926"),
        )
        val d2 = listOf<Uuid>()
        val d3 = listOf(Uuid.parse("55847eba-3ba1-4a18-b4af-4a9c49415272"))

        db.suspendTransaction {
            UuidArrayTestTable.insert {
                it[UuidArrayTestTable.key] = "foo"
                it[UuidArrayTestTable.data] = d1
            }
            UuidArrayTestTable.selectAll().where { UuidArrayTestTable.key eq "foo" }.single()
                .let(UuidArrayTestTable.Entity::create)
        }.also { row ->
            row.data shouldBe d1
            row.dataNullable shouldBe null
        }
        db.suspendTransaction {
            UuidArrayTestTable.insert {
                it[UuidArrayTestTable.key] = "bar"
                it[UuidArrayTestTable.data] = d3
                it[UuidArrayTestTable.dataNullable] = d2
            }
            UuidArrayTestTable.selectAll().where { UuidArrayTestTable.key eq "bar" }.single()
                .let(UuidArrayTestTable.Entity::create)
        }.also { row ->
            row.data shouldBe d3
            row.dataNullable shouldBe d2
        }
    }
}