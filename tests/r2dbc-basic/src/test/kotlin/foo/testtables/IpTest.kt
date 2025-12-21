package foo.testtables

import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.IpsTestTable
import inet.ipaddr.IPAddressString
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import kotlin.test.BeforeTest
import kotlin.test.Test

class IpTest {
    @BeforeTest
    fun cleanUp() = cleanUp(IpsTestTable)

    @Test
    fun `basic tests`(): Unit = runBlocking {
        val d1 = IPAddressString("192.168.1.10").toAddress()
        val d2 = IPAddressString("192.168.0.0").toAddress()
        db.suspendTransaction {
            IpsTestTable.insert {
                it[IpsTestTable.key] = "foo"
                it[IpsTestTable.i] = d1
            }
            IpsTestTable.selectAll().where { IpsTestTable.key eq "foo" }.single()
        }.also { row ->
            row[IpsTestTable.i] shouldBe d1
            row[IpsTestTable.iNullable] shouldBe null
        }
        db.suspendTransaction {
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