package foo

import de.quati.pgen.jdbc.util.transaction
import de.quati.pgen.tests.jdbc.basic.generated.db.foo._public.PgenTestTable
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.test.BeforeTest
import kotlin.test.Test

class PgenTestTableTest {
    @BeforeTest
    fun cleanUp() {
        cleanUpAll()
    }

    @Test
    fun `ranges and citext tests`() {
        db.transaction {
            PgenTestTable.insert {
                it[PgenTestTable.key] = "FooBar"
                it[PgenTestTable.iRange] = 3..47
                it[PgenTestTable.lRange] = 6L..9L
            }
            PgenTestTable.selectAll().where { PgenTestTable.key eq "FooBar" }.single()
        }.also { row ->
            row[PgenTestTable.key] shouldBe "FooBar"
            row[PgenTestTable.iRange] shouldBe 3..47
            row[PgenTestTable.iRangeNullable] shouldBe null
            row[PgenTestTable.lRange] shouldBe 6L..9L
            row[PgenTestTable.lRangeNullable] shouldBe null
        }
        db.transaction {
            PgenTestTable.insert {
                it[PgenTestTable.key] = "Hello World"
                it[PgenTestTable.iRange] = 3..47
                it[PgenTestTable.iRangeNullable] = 5..48
                it[PgenTestTable.lRange] = 6L..9L
                it[PgenTestTable.lRangeNullable] = 1L..3L
            }
            PgenTestTable.selectAll().where { PgenTestTable.key eq "Hello World" }.single()
        }.also { row ->
            row[PgenTestTable.key] shouldBe "Hello World"
            row[PgenTestTable.iRange] shouldBe 3..47
            row[PgenTestTable.iRangeNullable] shouldBe 5..48
            row[PgenTestTable.lRange] shouldBe 6L..9L
            row[PgenTestTable.lRangeNullable] shouldBe 1L..3L
        }
    }
}