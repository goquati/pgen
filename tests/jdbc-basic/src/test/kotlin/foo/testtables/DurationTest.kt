package foo.testtables

import de.quati.pgen.jdbc.util.transaction
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.DurationTestTable
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DateTimePeriod
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.test.BeforeTest
import kotlin.test.Test

class DurationTest {
    @BeforeTest
    fun cleanUp() = cleanUp(DurationTestTable)

    @Test
    fun `basic tests`() {
        val d1 = DateTimePeriod.parse("P4Y1DT2H3M4.058S")
        val d2 = DateTimePeriod.parse("P4M3DT2H7M4.058S")
        db.transaction {
            DurationTestTable.insert {
                it[DurationTestTable.key] = "foo"
                it[DurationTestTable.duration] = d1
            }
            DurationTestTable.selectAll().where { DurationTestTable.key eq "foo" }.single()
        }.also { row ->
            row[DurationTestTable.duration] shouldBe d1
            row[DurationTestTable.durationNullable] shouldBe null
        }
        db.transaction {
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