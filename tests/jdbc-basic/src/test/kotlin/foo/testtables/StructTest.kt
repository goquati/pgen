package foo.testtables

import de.quati.pgen.jdbc.util.transaction
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.Address
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.StructTestTable
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.test.BeforeTest
import kotlin.test.Test

class StructTest {
    @BeforeTest
    fun cleanUp() = cleanUp(StructTestTable)

    @Test
    fun `basic tests`() {
        val d1 = Address(street = "Foo Street", city = "Foo City", postalCode = "12345", country = "Foo Country")
        val d2 = Address(street = "Bar Street", city = "Bar City", postalCode = "67890", country = "Bar Country")
        db.transaction {
            StructTestTable.insert {
                it[StructTestTable.key] = "foo"
                it[StructTestTable.address] = d1
            }
            StructTestTable.selectAll().where { StructTestTable.key eq "foo" }.single()
        }.also { row ->
            row[StructTestTable.address] shouldBe d1
            row[StructTestTable.addressNullable] shouldBe null
        }
        db.transaction {
            StructTestTable.insert {
                it[StructTestTable.key] = "bar"
                it[StructTestTable.address] = d1
                it[StructTestTable.addressNullable] = d2
            }
            StructTestTable.selectAll().where { StructTestTable.key eq "bar" }.single()
        }.also { row ->
            row[StructTestTable.address] shouldBe d1
            row[StructTestTable.addressNullable] shouldBe d2
        }
    }
}