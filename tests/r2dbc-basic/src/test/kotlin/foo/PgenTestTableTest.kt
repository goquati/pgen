package foo

import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.r2dbc.util.sync
import de.quati.pgen.shared.RegClass
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.Address
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.Ips
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.PgenTestTable
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo._public.SyncTestTable
import inet.ipaddr.IPAddressString
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimePeriod
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class PgenTestTableTest {
    @BeforeTest
    fun cleanUp(): Unit = runBlocking { cleanUpAll() }


    @Test
    fun `ranges and citext tests`(): Unit = runBlocking {
        val d1 = DateTimePeriod.parse("P4Y1DT2H3M4.058S")
        val d2 = DateTimePeriod.parse("P4M3DT2H7M4.058S")
        val a1 = Address(street = "Foo Street", city = "Foo City", postalCode = "12345", country = "Foo Country")
        val a2 = Address(street = "Bar Street", city = "Bar City", postalCode = "67890", country = "Bar Country")
        val t1 = RegClass("public.pgen_test_table")
        val t2 = RegClass("public.sync_test_table")

        db.suspendTransaction {
            PgenTestTable.insert {
                it[PgenTestTable.key] = "FooBar"
                it[PgenTestTable.duration] = d1
                it[PgenTestTable.iRange] = 3..47
                it[PgenTestTable.lRange] = 6L..9L
                it[PgenTestTable.address] = a1
                it[PgenTestTable.table] = t1
            }
            PgenTestTable.selectAll().where { PgenTestTable.key eq "foobar" }.single()
        }.also { row ->
            row[PgenTestTable.key] shouldBe "FooBar"
            row[PgenTestTable.duration] shouldBe d1
            row[PgenTestTable.durationNullable] shouldBe null
            row[PgenTestTable.iRange] shouldBe 3..47
            row[PgenTestTable.iRangeNullable] shouldBe null
            row[PgenTestTable.lRange] shouldBe 6L..9L
            row[PgenTestTable.lRangeNullable] shouldBe null
            row[PgenTestTable.address] shouldBe a1
            row[PgenTestTable.addressNullable] shouldBe null
            row[PgenTestTable.table] shouldBe t1
            row[PgenTestTable.tableNullable] shouldBe null
        }
        db.suspendTransaction {
            PgenTestTable.insert {
                it[PgenTestTable.key] = "Hello World"
                it[PgenTestTable.duration] = d1
                it[PgenTestTable.durationNullable] = d2
                it[PgenTestTable.iRange] = 3..47
                it[PgenTestTable.iRangeNullable] = 5..48
                it[PgenTestTable.lRange] = 6L..9L
                it[PgenTestTable.lRangeNullable] = 1L..3L
                it[PgenTestTable.address] = a1
                it[PgenTestTable.addressNullable] = a2
                it[PgenTestTable.table] = t1
                it[PgenTestTable.tableNullable] = t2
            }
            PgenTestTable.selectAll().where { PgenTestTable.key eq "hello world" }.single()
        }.also { row ->
            row[PgenTestTable.key] shouldBe "Hello World"
            row[PgenTestTable.duration] shouldBe d1
            row[PgenTestTable.durationNullable] shouldBe d2
            row[PgenTestTable.iRange] shouldBe 3..47
            row[PgenTestTable.iRangeNullable] shouldBe 5..48
            row[PgenTestTable.lRange] shouldBe 6L..9L
            row[PgenTestTable.lRangeNullable] shouldBe 1L..3L
            row[PgenTestTable.address] shouldBe a1
            row[PgenTestTable.addressNullable] shouldBe a2
            row[PgenTestTable.table] shouldBe t1
            row[PgenTestTable.tableNullable] shouldBe t2
        }
    }

    @Test
    fun `test sync statement`(): Unit = runBlocking {
        val g1 = UUID.randomUUID()
        val g2 = UUID.randomUUID()
        suspend fun loadData() = db.suspendTransaction(readOnly = false) {
            SyncTestTable.selectAll().toList()
        }.groupBy({ it[SyncTestTable.groupId] }, { it[SyncTestTable.name] })
            .mapValues { it.value.toSet() }

        db.suspendTransaction {
            SyncTestTable.sync(
                key = SyncTestTable.groupId to g1,
                data = listOf(1, 2, 3),
            ) {
                this[SyncTestTable.name] = it.toString()
            }
        }
        loadData() shouldBe mapOf(g1 to setOf("1", "2", "3"))

        db.suspendTransaction {
            SyncTestTable.sync(
                key = SyncTestTable.groupId to g2,
                data = listOf(2, 3, 4),
            ) {
                this[SyncTestTable.name] = it.toString()
            }
        }
        loadData() shouldBe mapOf(g1 to setOf("1", "2", "3"), g2 to setOf("2", "3", "4"))

        db.suspendTransaction {
            SyncTestTable.sync(
                key = SyncTestTable.groupId to g1,
                data = listOf(3, 4),
            ) {
                this[SyncTestTable.name] = it.toString()
            }
        }
        loadData() shouldBe mapOf(g1 to setOf("3", "4"), g2 to setOf("2", "3", "4"))

        db.suspendTransaction {
            SyncTestTable.sync(
                key = SyncTestTable.groupId to g2,
                data = listOf<Int>(),
            ) {
                this[SyncTestTable.name] = it.toString()
            }
        }
        loadData() shouldBe mapOf(g1 to setOf("3", "4"))
    }

    @Test
    fun `test cidr and inet`(): Unit = runBlocking {
        val i1 = IPAddressString("192.168.1.10").toAddress()
        val c1 = IPAddressString("192.168.1.0/24").toAddress()

        db.suspendTransaction {
            Ips.insert {
                it[Ips.key] = "FooBar"
                it[Ips.i] = i1
                it[Ips.c] = c1
            }
            Ips.selectAll().where { Ips.key eq "FooBar" }.single()
        }.also { row ->
            row[Ips.i] shouldBe i1
            row[Ips.iNullable] shouldBe null
            row[Ips.c] shouldBe c1
            row[Ips.cNullable] shouldBe null
        }
    }
}