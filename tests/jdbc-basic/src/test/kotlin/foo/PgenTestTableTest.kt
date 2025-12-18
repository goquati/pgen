package foo

import de.quati.pgen.jdbc.util.sync
import de.quati.pgen.jdbc.util.transaction
import de.quati.pgen.shared.RegClass
import de.quati.pgen.tests.jdbc.basic.generated.db.foo._public.Address
import de.quati.pgen.tests.jdbc.basic.generated.db.foo._public.EnumArrayTestTable
import de.quati.pgen.tests.jdbc.basic.generated.db.foo._public.Ips
import de.quati.pgen.tests.jdbc.basic.generated.db.foo._public.OrderStatus
import de.quati.pgen.tests.jdbc.basic.generated.db.foo._public.PgenTestTable
import de.quati.pgen.tests.jdbc.basic.generated.db.foo._public.SyncTestTable
import inet.ipaddr.IPAddressString
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DateTimePeriod
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class PgenTestTableTest {
    @BeforeTest
    fun cleanUp() {
        cleanUpAll()
    }

    @Test
    fun `ranges and citext tests`() {
        val d1 = DateTimePeriod.parse("P4Y1DT2H3M4.058S")
        val d2 = DateTimePeriod.parse("P4M3DT2H7M4.058S")
        val a1 = Address(street = "Foo Street", city = "Foo City", postalCode = "12345", country = "Foo Country")
        val a2 = Address(street = "Bar Street", city = "Bar City", postalCode = "67890", country = "Bar Country")
        val t1 = RegClass("public.pgen_test_table")
        val t2 = RegClass("public.sync_test_table")

        db.transaction {
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
        db.transaction {
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
    fun `test sync statement`() {
        val g1 = UUID.randomUUID()
        val g2 = UUID.randomUUID()
        fun loadData() = db.transaction(readOnly = false) {
            SyncTestTable.selectAll().toList()
        }.groupBy({ it[SyncTestTable.groupId] }, { it[SyncTestTable.name] })
            .mapValues { it.value.toSet() }

        db.transaction {
            SyncTestTable.sync(
                key = SyncTestTable.groupId to g1,
                data = listOf(1, 2, 3),
            ) {
                this[SyncTestTable.name] = it.toString()
            }
        }
        loadData() shouldBe mapOf(g1 to setOf("1", "2", "3"))

        db.transaction {
            SyncTestTable.sync(
                key = SyncTestTable.groupId to g2,
                data = listOf(2, 3, 4),
            ) {
                this[SyncTestTable.name] = it.toString()
            }
        }
        loadData() shouldBe mapOf(g1 to setOf("1", "2", "3"), g2 to setOf("2", "3", "4"))

        db.transaction {
            SyncTestTable.sync(
                key = SyncTestTable.groupId to g1,
                data = listOf(3, 4),
            ) {
                this[SyncTestTable.name] = it.toString()
            }
        }
        loadData() shouldBe mapOf(g1 to setOf("3", "4"), g2 to setOf("2", "3", "4"))

        db.transaction {
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
    fun `test cidr and inet`() {
        val i1 = IPAddressString("192.168.1.10").toAddress()
        val i2 = IPAddressString("192.168.0.0").toAddress()

        db.transaction {
            Ips.insert {
                it[Ips.key] = "FooBar"
                it[Ips.i] = i1
            }
            Ips.selectAll().where { Ips.key eq "FooBar" }.single()
        }.also { row ->
            row[Ips.i] shouldBe i1
            row[Ips.iNullable] shouldBe null
        }

        db.transaction {
            Ips.insert {
                it[Ips.key] = "Hello"
                it[Ips.i] = i1
                it[Ips.iNullable] = i2
            }
            Ips.selectAll().where { Ips.key eq "Hello" }.single()
        }.also { row ->
            row[Ips.i] shouldBe i1
            row[Ips.iNullable] shouldBe i2
        }

        db.transaction(readOnly = true) { Ips.selectAll().count() } shouldBe 2
    }

    @Test
    fun `test enum array`() {
        val a1 = listOf(OrderStatus.PENDING, OrderStatus.PAID)
        val a2 = listOf<OrderStatus>()
        val a3 = listOf(OrderStatus.CANCELLED)

        db.transaction {
            EnumArrayTestTable.insert {
                it[EnumArrayTestTable.key] = "foo"
                it[EnumArrayTestTable.data] = a1
            }

            EnumArrayTestTable.selectAll().where { EnumArrayTestTable.key eq "foo" }.single()
                .let(EnumArrayTestTable.Entity::create)
        }.also { row ->
            row.data shouldBe a1
            row.dataNullable shouldBe null
        }

        db.transaction {
            EnumArrayTestTable.insert {
                it[EnumArrayTestTable.key] = "bar"
                it[EnumArrayTestTable.data] = a2
                it[EnumArrayTestTable.dataNullable] = a3
            }
            EnumArrayTestTable.selectAll().where { EnumArrayTestTable.key eq "bar" }.single()
                .let(EnumArrayTestTable.Entity::create)
        }.also { row ->
            row.data shouldBe a2
            row.dataNullable shouldBe a3
        }
    }
}