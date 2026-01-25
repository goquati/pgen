import de.quati.pgen.jdbc.util.emitLogicalPgMessage
import de.quati.pgen.jdbc.util.transaction
import de.quati.pgen.shared.WalEvent
import de.quati.pgen.tests.jdbc.wal.URL
import de.quati.pgen.tests.jdbc.wal.createDb
import de.quati.pgen.tests.jdbc.wal.generated.db.base.public1.BaseSchemaPublic
import de.quati.pgen.tests.jdbc.wal.generated.db.base.public1.CitextTestTable
import de.quati.pgen.tests.jdbc.wal.generated.db.base.public1.DateTimeTestTable
import de.quati.pgen.tests.jdbc.wal.generated.db.base.public1.DomainTestTable
import de.quati.pgen.tests.jdbc.wal.generated.db.base.public1.EnumArrayTestTable
import de.quati.pgen.tests.jdbc.wal.generated.db.base.public1.EnumTestTable
import de.quati.pgen.tests.jdbc.wal.generated.db.base.public1.OrderId
import de.quati.pgen.tests.jdbc.wal.generated.db.base.public1.OrderStatus
import de.quati.pgen.tests.jdbc.wal.shared.UserId
import de.quati.pgen.wal.PgenWalEventListener
import de.quati.pgen.wal.pgenWalListener
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import org.postgresql.PGProperty
import java.time.ZoneId
import kotlin.uuid.Uuid
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.toJavaInstant

class WalTest {
    private val d1 = DomainTestTable.Entity(
        key = "d1",
        userIdNullable = null,
        userId = UserId(Uuid.random()),
        orderIdNullable = null,
        orderId = OrderId(Uuid.random()),
    )
    private val d2 = d1.copy(
        userIdNullable = UserId(Uuid.random()),
        orderIdNullable = OrderId(Uuid.random()),
    )
    private val e1 = EnumTestTable.Entity(
        key = "e1",
        enumerationNullable = null,
        enumeration = OrderStatus.PENDING,
    )
    private val e2 = e1.copy(enumerationNullable = OrderStatus.PAID)
    private val c1 = CitextTestTable.Entity(
        key = "c1",
        textNullable = null,
        text = "foo",
    )
    private val c2 = c1.copy(textNullable = "bar")
    private val t1 = DateTimeTestTable.Entity(
        key = "t1",
        ts = Instant.fromEpochSeconds(500_000),
        tsNullable = null,
        tsz = Instant.fromEpochSeconds(600_000).toJavaInstant()
            .atZone(ZoneId.systemDefault())
            .toOffsetDateTime(),
        tszNullable = null,
        date = LocalDate(2023, 1, 1),
        dateNullable = null,
        time = LocalTime(12, 5, 3),
        timeNullable = null,
    )
    private val t2 = t1.copy(
        tsNullable = Instant.fromEpochSeconds(2_000_000),
        tszNullable = Instant.fromEpochSeconds(1_000_000).toJavaInstant()
            .atZone(ZoneId.systemDefault())
            .toOffsetDateTime(),
        dateNullable = LocalDate(2000, 1, 1),
        timeNullable = LocalTime(16, 5, 3),
    )
    private val a1 = EnumArrayTestTable.Entity(
        key = "a1",
        data = listOf(OrderStatus.PENDING, OrderStatus.REFUNDED),
        dataNullable = null,
    )
    private val a2 = a1.copy(
        dataNullable = listOf(OrderStatus.CANCELLED)
    )

    private fun Database.cleanup() = transaction {
        DomainTestTable.deleteAll()
        EnumTestTable.deleteAll()
        CitextTestTable.deleteAll()
        DateTimeTestTable.deleteAll()
        EnumArrayTestTable.deleteAll()
    }

    private fun Database.sendStopMessage(msg: String) = transaction {
        emitLogicalPgMessage(transactional = true, prefix = "foobar", message = msg, flush = true)
    }

    private fun Database.exampleChanges() = transaction {
        DomainTestTable.insert { d1 applyTo it }
        DomainTestTable.update(where = { DomainTestTable.key eq d1.key }) { d2 applyTo it }
        DomainTestTable.deleteWhere { DomainTestTable.key eq d2.key }
        EnumTestTable.insert { e1 applyTo it }
        EnumTestTable.update(where = { EnumTestTable.key eq e1.key }) { e2 applyTo it }
        EnumTestTable.deleteWhere { EnumTestTable.key eq e2.key }
        CitextTestTable.insert { c1 applyTo it }
        CitextTestTable.update(where = { CitextTestTable.key eq c1.key }) { c2 applyTo it }
        CitextTestTable.deleteWhere { CitextTestTable.key eq c2.key }
        DateTimeTestTable.insert { t1 applyTo it }
        DateTimeTestTable.update(where = { DateTimeTestTable.key eq t1.key }) { t2 applyTo it }
        DateTimeTestTable.deleteWhere { DateTimeTestTable.key eq t2.key }
        EnumArrayTestTable.insert { a1 applyTo it }
        EnumArrayTestTable.update(where = { EnumArrayTestTable.key eq a1.key }) { a2 applyTo it }
        EnumArrayTestTable.deleteWhere { EnumArrayTestTable.key eq a2.key }
    }

    private val expectedEvents = listOf(
        d1.toInsertEvent(),
        (d1 to d2).toUpdateEvent(),
        d2.toDeleteEvent(),
        e1.toInsertEvent(),
        (e1 to e2).toUpdateEvent(),
        e2.toDeleteEvent(),
        c1.toInsertEvent(),
        (c1 to c2).toUpdateEvent(),
        c2.toDeleteEvent(),
        t1.toInsertEvent(),
        (t1 to t2).toUpdateEvent(),
        t2.toDeleteEvent(),
        a1.toInsertEvent(),
        (a1 to a2).toUpdateEvent(),
        a2.toDeleteEvent(),
    ).map(::CompareEvent)

    context(scope: CoroutineScope)
    private suspend fun PgenWalEventListener.testCollect(
        recreateSlot: Boolean,
        stopMsg: String,
        action: suspend () -> Unit,
    ): List<WalEvent.Change<*>> {
        this@testCollect.start(recreateSlot = recreateSlot)
        val readyFlag = ReadyFlag()
        val job = scope.async {
            this@testCollect.flow
                .onSubscription { readyFlag.markReady() }
                .takeWhile { (it as? WalEvent.Message)?.content != stopMsg }
                .map {
                    (it as? WalEvent.Change<*>)
                        ?: error("Expected WalEvent.Change, got $it")
                }
                .toList()
        }
        readyFlag.awaitReady()
        action()
        val result = job.await()
        this@testCollect.stop()
        return result
    }

    @Test
    fun `test WAL event streaming with jdbc`(): Unit = blockingWithTimeout(timeout = 60.seconds) {
        val db = createDb()
        db.cleanup()

        val listener = pgenWalListener(
            slot = "foobar",
            url = URL,
        ) {
            statusUpdateInterval(0.milliseconds) // send status updates directly
            addPgProperty(PGProperty.USER, "postgres")
            addPgProperty(PGProperty.PASSWORD, "postgres")
            addTable(BaseSchemaPublic.eventTables)
        }

        val events1 = listener.testCollect(recreateSlot = true, stopMsg = "stop1") {
            db.exampleChanges()
            db.sendStopMessage("stop1")
        }
        events1.map(::CompareEvent) shouldBe expectedEvents

        val events2 = listener.testCollect(recreateSlot = false, stopMsg = "stop2") {
            db.sendStopMessage("stop2")
        }
        events2.size shouldBe 0 // all events should be acknowledged
    }
}