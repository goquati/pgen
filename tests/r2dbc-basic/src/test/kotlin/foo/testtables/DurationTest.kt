package foo.testtables

import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo.public1.DurationTestTable
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimePeriod
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import kotlin.test.BeforeTest
import kotlin.test.Test

class DurationTest {
    @BeforeTest
    fun cleanUp() = cleanUp(DurationTestTable)

    @Test
    fun `basic tests`(): Unit = runBlocking {
        val d1 = DateTimePeriod.parse("P4Y1DT2H3M4.058S")
        val d2 = DateTimePeriod.parse("P4M3DT2H7M4.058S")
        db.suspendTransaction {
            DurationTestTable.insert {
                it[DurationTestTable.key] = "foo"
                it[DurationTestTable.duration] = d1
            }
            DurationTestTable.selectAll().where { DurationTestTable.key eq "foo" }.single()
        }.also { row ->
            row[DurationTestTable.duration] shouldBe d1
            row[DurationTestTable.durationNullable] shouldBe null
        }
        db.suspendTransaction {
            DurationTestTable.insert {
                it[DurationTestTable.key] = "bar"
                it[DurationTestTable.duration] = d1
                it[DurationTestTable.durationNullable] = d2
            }
            DurationTestTable.selectAll().where { DurationTestTable.key eq "bar" }.single()
        }.also { row ->
            row[DurationTestTable.duration] shouldBe d1
            row[DurationTestTable.durationNullable] shouldBe d2
        }
    }
}