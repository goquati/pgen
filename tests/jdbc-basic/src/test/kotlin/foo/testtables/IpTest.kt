package foo.testtables

import de.quati.pgen.jdbc.util.transaction
import de.quati.pgen.tests.jdbc.basic.generated.db.foo.public1.IpsTestTable
import inet.ipaddr.IPAddressString
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.test.BeforeTest
import kotlin.test.Test

class IpTest {
    @BeforeTest
    fun cleanUp() = cleanUp(IpsTestTable)

    @Test
    fun `basic tests`() {
        val d1 = IPAddressString("192.168.1.10").toAddress()
        val d2 = IPAddressString("192.168.0.0").toAddress()
        db.transaction {
            IpsTestTable.insert {
                it[IpsTestTable.key] = "foo"
                it[IpsTestTable.i] = d1
            }
            IpsTestTable.selectAll().where { IpsTestTable.key eq "foo" }.single()
        }.also { row ->
            row[IpsTestTable.i] shouldBe d1
            row[IpsTestTable.iNullable] shouldBe null
        }
        db.transaction {
            IpsTestTable.insert {
                it[IpsTestTable.key] = "bar"
                it[IpsTestTable.i] = d1
                it[IpsTestTable.iNullable] = d2
            }
            IpsTestTable.selectAll().where { IpsTestTable.key eq "bar" }.single()
        }.also { row ->
            row[IpsTestTable.i] shouldBe d1
            row[IpsTestTable.iNullable] shouldBe d2
        }
    }
}