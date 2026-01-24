package foo.testtables

import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo.public1.EnumArrayTestTable
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo.public1.OrderStatus
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import kotlin.test.BeforeTest
import kotlin.test.Test

class EnumArrayTest {
    @BeforeTest
    fun cleanUp() = cleanUp(EnumArrayTestTable)

    @Test
    fun `basic tests`(): Unit = runBlocking {
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
}