package foo.testtables

import de.quati.pgen.jdbc.util.transaction
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.EnumArrayTestTable
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.OrderStatus
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.get
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.test.BeforeTest
import kotlin.test.Test

class EnumArrayTest {
    @BeforeTest
    fun cleanUp() = cleanUp(EnumArrayTestTable)

    @Test
    fun `basic tests`() {
        val d1 = listOf(OrderStatus.PENDING, OrderStatus.PAID)
        val d2 = listOf<OrderStatus>()
        val d3 = listOf(OrderStatus.CANCELLED)

        db.transaction {
            EnumArrayTestTable.insert {
                it[EnumArrayTestTable.key] = "foo"
                it[EnumArrayTestTable.data] = d1
            }
            EnumArrayTestTable.selectAll().where { EnumArrayTestTable.key eq "foo" }.single()
                .let(EnumArrayTestTable.Entity::create)
        }.also { row ->
            row.data shouldBe d1
            row.dataNullable shouldBe null
        }
        db.transaction {
            EnumArrayTestTable.insert {
                it[EnumArrayTestTable.key] = "bar"
                it[EnumArrayTestTable.data] = d3
                it[EnumArrayTestTable.dataNullable] = d2
            }
            EnumArrayTestTable.selectAll().where { EnumArrayTestTable.key eq "bar" }.single()
                .let(EnumArrayTestTable.Entity::create)
        }.also { row ->
            row.data shouldBe d3
            row.dataNullable shouldBe d2
        }
    }
}