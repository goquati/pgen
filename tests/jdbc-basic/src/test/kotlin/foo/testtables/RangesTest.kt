package foo.testtables

import de.quati.pgen.jdbc.util.transaction
import de.quati.pgen.tests.jdbc.basic.generated.db.foo._public.RangesTestTable
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.test.BeforeTest
import kotlin.test.Test

class RangesTest {
    @BeforeTest
    fun cleanUp() = cleanUp(RangesTestTable)

    @Test
    fun `basic tests`() {
        db.transaction {
            RangesTestTable.insert {
                it[RangesTestTable.key] = "foo"
                it[RangesTestTable.iRange] = 3..47
                it[RangesTestTable.lRange] = 6L..9L
            }
            RangesTestTable.selectAll().where { RangesTestTable.key eq "foo" }.single()
        }.also { row ->
            row[RangesTestTable.iRange] shouldBe 3..47
            row[RangesTestTable.iRangeNullable] shouldBe null
            row[RangesTestTable.lRange] shouldBe 6L..9L
            row[RangesTestTable.lRangeNullable] shouldBe null
        }
        db.transaction {
            RangesTestTable.insert {
                it[RangesTestTable.key] = "bar"
                it[RangesTestTable.iRange] = 3..47
                it[RangesTestTable.iRangeNullable] = 5..48
                it[RangesTestTable.lRange] = 6L..9L
                it[RangesTestTable.lRangeNullable] = 1L..3L
            }
            RangesTestTable.selectAll().where { RangesTestTable.key eq "bar" }.single()
        }.also { row ->
            row[RangesTestTable.iRange] shouldBe 3..47
            row[RangesTestTable.iRangeNullable] shouldBe 5..48
            row[RangesTestTable.lRange] shouldBe 6L..9L
            row[RangesTestTable.lRangeNullable] shouldBe 1L..3L
        }
    }
}