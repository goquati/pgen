import de.quati.pgen.shared.TableNameWithSchema
import de.quati.pgen.shared.WalEvent
import de.quati.pgen.tests.r2dbc.wal.generated.db.base.public1.CitextTestTable
import de.quati.pgen.tests.r2dbc.wal.generated.db.base.public1.DateTimeTestTable
import de.quati.pgen.tests.r2dbc.wal.generated.db.base.public1.DomainTestTable
import de.quati.pgen.tests.r2dbc.wal.generated.db.base.public1.EnumArrayTestTable
import de.quati.pgen.tests.r2dbc.wal.generated.db.base.public1.EnumTestTable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.OffsetDateTime
import kotlin.time.Duration

internal fun <T> blockingWithTimeout(
    timeout: Duration,
    block: suspend CoroutineScope.() -> T
): T = runBlocking {
    withTimeout(timeout, block)
}

internal class ReadyFlag {
    private val ready = CompletableDeferred<Unit>()

    fun markReady() {
        ready.complete(Unit)
    }

    suspend fun awaitReady() {
        ready.await()
    }
}

private val dummyMetaData = WalEvent.MetaData(timestamp = OffsetDateTime.MIN)
typealias EntityPair<T> = Pair<T, T>

private fun DomainTestTable.Entity.toEvent() = DomainTestTable.EventEntity(
    key = key,
    userIdNullable = userIdNullable,
    userId = userId,
    orderIdNullable = orderIdNullable,
    orderId = orderId,
)

private fun EnumTestTable.Entity.toEvent() = EnumTestTable.EventEntity(
    key = key,
    enumerationNullable = enumerationNullable,
    enumeration = enumeration,
)

private fun CitextTestTable.Entity.toEvent() = CitextTestTable.EventEntity(
    key = key,
    textNullable = textNullable,
    text = text,
)

private fun DateTimeTestTable.Entity.toEvent() = DateTimeTestTable.EventEntity(
    key = key,
    ts = ts,
    tsNullable = tsNullable,
    date = date,
    dateNullable = dateNullable,
    time = time,
    timeNullable = timeNullable,
    tsz = tsz,
    tszNullable = tszNullable,
)

private fun EnumArrayTestTable.Entity.toEvent() = EnumArrayTestTable.EventEntity(
    key = key,
)

data class CompareEvent(
    val table: TableNameWithSchema,
    val payload: WalEvent.Change.Payload<*>,
) {
    constructor(event: WalEvent.Change<*>) : this(event.table, event.payload)
}

fun DomainTestTable.Entity.toInsertEvent() = DomainTestTable.Event(
    dummyMetaData, WalEvent.Change.Payload.Insert(toEvent()),
)

fun EntityPair<DomainTestTable.Entity>.toUpdateEvent() = DomainTestTable.Event(
    dummyMetaData, WalEvent.Change.Payload.Update(first.toEvent(), second.toEvent()),
)

fun DomainTestTable.Entity.toDeleteEvent() = DomainTestTable.Event(
    dummyMetaData, WalEvent.Change.Payload.Delete(toEvent()),
)

fun EnumTestTable.Entity.toInsertEvent() = EnumTestTable.Event(
    dummyMetaData, WalEvent.Change.Payload.Insert(toEvent()),
)

fun EntityPair<EnumTestTable.Entity>.toUpdateEvent() = EnumTestTable.Event(
    dummyMetaData, WalEvent.Change.Payload.Update(first.toEvent(), second.toEvent()),
)

fun EnumTestTable.Entity.toDeleteEvent() = EnumTestTable.Event(
    dummyMetaData, WalEvent.Change.Payload.Delete(toEvent()),
)

fun CitextTestTable.Entity.toInsertEvent() = CitextTestTable.Event(
    dummyMetaData, WalEvent.Change.Payload.Insert(toEvent()),
)

fun EntityPair<CitextTestTable.Entity>.toUpdateEvent() = CitextTestTable.Event(
    dummyMetaData, WalEvent.Change.Payload.Update(first.toEvent(), second.toEvent()),
)

fun CitextTestTable.Entity.toDeleteEvent() = CitextTestTable.Event(
    dummyMetaData, WalEvent.Change.Payload.Delete(toEvent()),
)

fun DateTimeTestTable.Entity.toInsertEvent() = DateTimeTestTable.Event(
    dummyMetaData, WalEvent.Change.Payload.Insert(toEvent()),
)

fun EntityPair<DateTimeTestTable.Entity>.toUpdateEvent() = DateTimeTestTable.Event(
    dummyMetaData, WalEvent.Change.Payload.Update(first.toEvent(), second.toEvent()),
)

fun DateTimeTestTable.Entity.toDeleteEvent() = DateTimeTestTable.Event(
    dummyMetaData, WalEvent.Change.Payload.Delete(toEvent()),
)



fun EnumArrayTestTable.Entity.toInsertEvent() = EnumArrayTestTable.Event(
    dummyMetaData, WalEvent.Change.Payload.Insert(toEvent()),
)

fun EntityPair<EnumArrayTestTable.Entity>.toUpdateEvent() = EnumArrayTestTable.Event(
    dummyMetaData, WalEvent.Change.Payload.Update(first.toEvent(), second.toEvent()),
)

fun EnumArrayTestTable.Entity.toDeleteEvent() = EnumArrayTestTable.Event(
    dummyMetaData, WalEvent.Change.Payload.Delete(toEvent()),
)
