package foo.testtables

import de.quati.pgen.r2dbc.util.suspendTransaction
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo.public1.DomainTestTable
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo.public1.Email
import de.quati.pgen.tests.r2dbc.basic.generated.db.foo.public1.OrderId
import de.quati.pgen.tests.r2dbc.basic.shared.UserId
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class DomainTest {

    @Test
    fun gg() = runBlocking(Dispatchers.IO) {

        val job = launch {
            db.suspendTransaction {
                this.exec("SET statement_timeout = 1000; select pg_sleep(10)")
            }
        }

        delay(1000)
        job.cancelAndJoin()
    }

    @Test
    fun `domain type tests`(): Unit = runBlocking {
        cleanUp(DomainTestTable)
        val u1 = UserId(Uuid.parse("a75d59ec-7aca-41c7-9dc0-19ebf3391298"))
        val o1 = OrderId(Uuid.parse("1b985967-6bdc-420e-be1e-12b0ffea9926"))
        val e1 = Email("foo@bar.com")
        val u2 = UserId(Uuid.parse("b75d59ec-7aca-41c7-9dc0-19ebf3391298"))
        val o2 = OrderId(Uuid.parse("2b985967-6bdc-420e-be1e-12b0ffea9926"))
        val e2 = Email("gg@bar.com")
        db.suspendTransaction {
            DomainTestTable.insert {
                it[DomainTestTable.key] = "foo"
                it[DomainTestTable.userId] = u1
                it[DomainTestTable.orderId] = o1
                it[DomainTestTable.email] = e1
            }
            DomainTestTable.selectAll().where { DomainTestTable.key eq "foo" }.single()
        }.also { row ->
            row[DomainTestTable.userId] shouldBe u1
            row[DomainTestTable.userIdNullable] shouldBe null
            row[DomainTestTable.orderId] shouldBe o1
            row[DomainTestTable.orderIdNullable] shouldBe null
            row[DomainTestTable.email] shouldBe e1
            row[DomainTestTable.emailNullable] shouldBe null
        }
        db.suspendTransaction {
            DomainTestTable.insert {
                it[DomainTestTable.key] = "bar"
                it[DomainTestTable.userId] = u2
                it[DomainTestTable.userIdNullable] = u1
                it[DomainTestTable.orderId] = o2
                it[DomainTestTable.orderIdNullable] = o1
                it[DomainTestTable.email] = e2
                it[DomainTestTable.emailNullable] = e1
            }
            DomainTestTable.selectAll().where { DomainTestTable.key eq "bar" }.single()
        }.also { row ->
            row[DomainTestTable.userId] shouldBe u2
            row[DomainTestTable.userIdNullable] shouldBe u1
            row[DomainTestTable.orderId] shouldBe o2
            row[DomainTestTable.orderIdNullable] shouldBe o1
            row[DomainTestTable.email] shouldBe e2
            row[DomainTestTable.emailNullable] shouldBe e1
        }
        db.suspendTransaction {
            DomainTestTable.selectAll().where { DomainTestTable.userId eq u2 }.single()
        }.also { row -> row[DomainTestTable.key] shouldBe "bar" }
    }
}