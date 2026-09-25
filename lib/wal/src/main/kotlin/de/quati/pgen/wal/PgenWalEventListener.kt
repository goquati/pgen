package de.quati.pgen.wal

import de.quati.pgen.core.PgenWalEventTable
import de.quati.pgen.shared.TableNameWithSchema
import de.quati.pgen.shared.WalEvent
import de.quati.pgen.shared.requireValidPgIdentifier
import de.quati.pgen.wal.ReplicaIdentity.Companion.toSql
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import org.postgresql.PGConnection
import org.postgresql.PGProperty
import org.postgresql.replication.PGReplicationStream
import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Listens to PostgreSQL logical replication WAL events using a logical replication slot
 * and exposes table change events as a cold-to-hot [SharedFlow].
 *
 * The listener uses a dedicated replication connection and streams changes via the
 * `wal2json` output plugin. Incoming WAL messages are parsed and mapped to [WalEvent]
 * instances based on the configured table mappings.
 *
 * ### Semantics
 * - WAL events are processed **best-effort**:
 *   parsing or mapping errors are logged, and the corresponding LSN is still acknowledged.
 * - LSNs are acknowledged per transaction: the commit LSN is acknowledged once all events
 *   of the transaction have been emitted to [flow]. If the listener is stopped or the
 *   connection is lost in between, the whole transaction is redelivered, so delivery is
 *   at-least-once.
 * - Backpressure is controlled via the underlying [MutableSharedFlow] configuration.
 *   Depending on its buffer and overflow strategy, slow collectors may suspend WAL
 *   consumption.
 * - The listener reconnects automatically on streaming errors using an exponential
 *   backoff strategy.
 *
 * ### Lifecycle
 * - [start] creates (optionally recreates) the replication slot and starts streaming.
 * - [stop] cancels the streaming coroutine and closes the replication connection.
 * - [deleteSlot] removes the replication slot and must not be called while running.
 *
 * ### Concurrency
 * - Only one active listener job is allowed at a time.
 * - All WAL streaming is executed on a single-threaded IO dispatcher.
 *
 * @property slot Name of the logical replication slot.
 * @property flow A shared flow emitting parsed [WalEvent]s for the configured tables.
 */
public class PgenWalEventListener private constructor(
    public val slot: String,
    private val url: String,
    private val properties: Map<PGProperty, String>,
    private val statusUpdateInterval: Duration,
    private val mutableSharedFlow: MutableSharedFlow<WalEvent<*>>,
    private val tableInfo: Map<TableNameWithSchema, TableInfo>,
) {
    private var job: Job? = null
    private val jobMutex = Mutex(locked = false)

    // only used by [stop] to force-close the connection if the streaming job does not terminate in time
    private val streamConnection = AtomicReference<Connection?>(null)

    /**
     * A shared flow emitting parsed WAL change events for the configured tables.
     *
     * The flow is hot and backed by a [MutableSharedFlow] whose buffering and overflow
     * behavior is defined by the [Builder] configuration.
     *
     * Emission happens in the WAL streaming coroutine; depending on the buffer and
     * overflow strategy, slow collectors may apply backpressure to WAL consumption.
     */
    public val flow: SharedFlow<WalEvent<*>> = mutableSharedFlow.asSharedFlow()

    init {
        requireValidPgIdentifier(slot, what = "slot")
    }

    /**
     * Starts the WAL event listener and begins streaming changes from PostgreSQL.
     *
     * This method:
     * 1. Optionally deletes the existing replication slot.
     * 2. Configures replica identities for all registered tables.
     * 3. Creates the logical replication slot if it does not already exist.
     * 4. Launches a background coroutine that consumes WAL events.
     *
     * If no tables are configured, the listener will not start and a warning is logged.
     *
     * @param recreateSlot If `true`, the replication slot is dropped and recreated before
     *                     starting the listener.
     *
     * @throws IllegalStateException if the listener is already running.
     */
    context(scope: CoroutineScope)
    public suspend fun start(recreateSlot: Boolean) {
        if (tableInfo.isEmpty()) {
            log.warn("no tables configured, listener will not start")
            return
        }
        jobMutex.withLock {
            require(job == null) { "Listener is already running" }
            createConnection().use { conn ->
                if (recreateSlot) conn.deleteSlot(name = slot)
                conn.setReplicaIdentities(tableInfo.mapValues { it.value.replicaIdentity })
                conn.createSlot(name = slot)
            }
            job = createJob()
        }
    }

    /**
     * Deletes the configured logical replication slot.
     *
     * The listener must not be running when this method is called.
     * If the slot does not exist, the operation is silently ignored.
     *
     * @throws IllegalStateException if the listener is currently running.
     */
    public suspend fun deleteSlot() {
        jobMutex.withLock {
            require(job == null) { "Cannot delete slot, listener is running" }
            createConnection().use { conn ->
                conn.deleteSlot(name = slot)
            }
        }
    }

    /**
     * Stops the WAL event listener.
     *
     * This method cancels the streaming coroutine and wakes up its blocking replication
     * read by emitting a (non-transactional) logical wake-up message, which is never
     * exposed via [flow]. The streaming coroutine thereby completes the WAL message it is
     * currently processing (and acknowledges it, if it completes a transaction) before it
     * closes the replication stream gracefully. A transaction whose commit has not been
     * acknowledged yet is redelivered on the next [start].
     *
     * If the wake-up message cannot be emitted or the streaming coroutine does not
     * terminate within 30 seconds, the replication connection is closed forcibly.
     *
     * Calling this method is idempotent; calling it when the listener is not running
     * has no effect.
     */
    public suspend fun stop() {
        jobMutex.withLock {
            val currentJob = job ?: return@withLock
            withContext(NonCancellable) {
                currentJob.cancel()
                val stopped = emitWakeUpMessage() &&
                    withTimeoutOrNull(STOP_TIMEOUT) { currentJob.join() } != null
                if (!stopped) {
                    log.warn("WAL listener did not stop gracefully, closing replication connection forcibly")
                    streamConnection.get()?.let { conn ->
                        runCatching { conn.unwrap(PGConnection::class.java).cancelQuery() }
                        runCatching { conn.close() }
                    }
                    currentJob.join()
                }
                job = null
            }
        }
    }

    /**
     * Emits a non-transactional logical message, which lets the blocking replication read of
     * the streaming job return, so that it can terminate gracefully.
     */
    private suspend fun emitWakeUpMessage(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            createConnection().use { conn ->
                // flush parameter is only available since PostgreSQL 17, otherwise the WAL writer flushes it shortly
                val sql = if (conn.metaData.databaseMajorVersion >= PG_VERSION_EMIT_MESSAGE_FLUSH)
                    "select pg_logical_emit_message(false, ?, '', true)"
                else
                    "select pg_logical_emit_message(false, ?, '')"
                conn.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, WAKE_UP_MESSAGE_PREFIX)
                    stmt.execute()
                }
            }
        }.onFailure {
            log.warn("failed to emit WAL wake-up message: ${it.message}")
        }.isSuccess
    }

    private fun createConnection(
        additionalProperties: Map<PGProperty, String>? = null
    ): Connection = DriverManager.getConnection(
        url,
        Properties().apply {
            properties.forEach { (key, value) ->
                key.set(this, value)
            }
            additionalProperties?.forEach { (key, value) ->
                key.set(this, value)
            }
        },
    )!!

    private inline fun Connection.usePgReplicationStream(block: (PGReplicationStream) -> Unit) {
        var stream: PGReplicationStream? = null
        try {
            stream = unwrap(PGConnection::class.java)
                .replicationAPI
                .replicationStream()
                .logical()
                .withSlotName(slot)
                .withStatusInterval(statusUpdateInterval.inWholeMilliseconds.toInt(), TimeUnit.MILLISECONDS)
                .withSlotOption("format-version", "2")
                .withSlotOption("include-transaction", "true")
                .withSlotOption("include-timestamp", "true")
                .withSlotOption("include-types", "false")
                .withSlotOption("include-typmod", "false")
                .withSlotOption("add-tables", tableInfo.keys.joinToString(separator = ","))
                .start()
            block(stream)
        } finally {
            stream?.close()
        }
    }

    context(scope: CoroutineScope)
    private fun createJob() = scope.launch(Dispatchers.IO.limitedParallelism(1)) {
        val backoff = Backoff(
            minDelay = 500.milliseconds,
            maxDelay = 8.seconds,
        )
        var reconnectionCount = 0
        while (isActive) {
            try {
                val conn = createConnection(
                    additionalProperties = mapOf(
                        PGProperty.ASSUME_MIN_SERVER_VERSION to "9.4",
                        PGProperty.REPLICATION to "database",
                    )
                )
                streamConnection.set(conn)
                try {
                    if (!isActive) break
                    if (reconnectionCount++ > 0)
                        log.info("created new replication connection (reconnection #${reconnectionCount - 1})")
                    conn.usePgReplicationStream { stream -> consumeStream(stream, backoff) }
                } finally {
                    streamConnection.compareAndSet(conn, null)
                    runCatching { conn.close() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                if (e is PSQLException &&
                    e.sqlState == SQL_STATE_QUERY_CANCELED &&
                    "due to user request" in (e.message ?: "")
                ) continue
                log.error("error during WAL-Event streaming: ${e.message}")
                backoff.incrementAndWait()
            }
        }
    }

    /**
     * Consumes the replication stream until the coroutine is canceled.
     *
     * Changes and transactional messages are only acknowledged with the LSN of their
     * transaction's commit record. PostgreSQL always redelivers a whole transaction whose
     * commit LSN is beyond the confirmed flush LSN, so acknowledging an LSN within a
     * transaction would lead to redelivery of the entire transaction anyway.
     *
     * Cancellation is checked between messages (the blocking read is woken up by [stop])
     * and while suspended in [MutableSharedFlow.emit]. Hence, a commit is only acknowledged
     * after all events of its transaction have been emitted.
     */
    private suspend fun CoroutineScope.consumeStream(stream: PGReplicationStream, backoff: Backoff) {
        var inTransaction = false
        while (isActive) {
            val buffer = stream.read() ?: error("replication stream is closed")
            val lsn = stream.lastReceiveLSN
            when (val message = parseMessage(buffer)) {
                WalEventIntern.Begin -> inTransaction = true
                WalEventIntern.Commit -> inTransaction = false
                is WalEventIntern.Change, is WalEventIntern.Message -> mapEvent(message)?.let {
                    mutableSharedFlow.emit(it)
                }

                null -> Unit
            }
            if (!inTransaction) {
                stream.setAppliedLSN(lsn)
                stream.setFlushedLSN(lsn)
                stream.forceUpdateStatus()
            }
            backoff.reset()
        }
    }

    private fun parseMessage(buffer: ByteBuffer): WalEventIntern? = try {
        val bytes = ByteArray(buffer.remaining()).also { bytes -> buffer.get(bytes) }
        WalEventIntern.parse(bytes)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        log.error("error during WAL-Event parsing: ${e.message}", e)
        null
    }

    private fun mapEvent(message: WalEventIntern): WalEvent<*>? = try {
        when (message) {
            is WalEventIntern.Change -> {
                val mapper = tableInfo[message.tableNameWithSchema]?.mapper
                    ?: error("no mapper for table ${message.tableNameWithSchema} found")
                message.toEvent(mapper)
            }

            is WalEventIntern.Message -> message.takeIf { it.prefix != WAKE_UP_MESSAGE_PREFIX }?.toEvent()
            is WalEventIntern.Transaction -> null
        }
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        log.error("error during WAL-Event mapping: ${e.message}", e)
        null
    }

    /**
     * Builder for creating a configured [PgenWalEventListener].
     *
     * The builder allows configuring:
     * - PostgreSQL connection properties
     * - WAL event buffering and backpressure behavior
     * - Tables to listen to and their mapping logic
     *
     * A listener created by this builder is inactive until [PgenWalEventListener.start]
     * is called.
     *
     * @property slot Name of the logical replication slot.
     * @property url JDBC connection URL for PostgreSQL.
     */
    public class Builder(
        public val slot: String,
        private val url: String,
    ) {
        private companion object {
            const val DEFAULT_FLOW_REPLAY = 0
            const val DEFAULT_FLOW_EXTRA_BUFFER_CAPACITY = 32
        }

        private var statusUpdateInterval = 10.seconds
        private val properties = mutableMapOf<PGProperty, String>()
        private var flowReplay = DEFAULT_FLOW_REPLAY
        private var flowExtraBufferCapacity = DEFAULT_FLOW_EXTRA_BUFFER_CAPACITY
        private var flowOnBufferOverflow = BufferOverflow.SUSPEND
        private val tableInfo = mutableMapOf<TableNameWithSchema, TableInfo>()

        /**
         * Sets the interval for replication status updates sent to PostgreSQL.
         *
         * The value is passed to `PGReplicationStream.withStatusInterval(...)` and controls
         * how often the client reports its applied and flushed LSNs to the server.
         * The default interval is 10 seconds.
         *
         * @param value Status update interval.
         */
        public fun statusUpdateInterval(value: Duration): Builder = apply { statusUpdateInterval = value }

        /**
         * Sets the number of replayed events for the listener's shared flow.
         *
         * @param value Number of events to replay to new subscribers.
         */
        public fun flowReplay(value: Int): Builder = apply { flowReplay = value }

        /**
         * Sets the extra buffer capacity for the listener's shared flow.
         *
         * @param value Number of additional buffered events beyond replay.
         */
        public fun flowExtraBufferCapacity(value: Int): Builder = apply { flowExtraBufferCapacity = value }

        /**
         * Sets the overflow strategy used when the shared flow buffer is full.
         *
         * @param value Buffer overflow strategy.
         */
        public fun flowOnBufferOverflow(value: BufferOverflow): Builder = apply { flowOnBufferOverflow = value }

        /**
         * Adds a PostgreSQL connection property.
         *
         * @param key PostgreSQL JDBC property key.
         * @param value Property value.
         */
        public fun addPgProperty(key: PGProperty, value: String): Builder = apply { properties[key] = value }

        /**
         * Registers multiple tables using their [PgenWalEventTable] definitions.
         *
         * Each table is configured with FULL replica identity and its associated WAL
         * event mapper.
         *
         * @param tables Tables to register.
         */
        public fun addTable(tables: Iterable<PgenWalEventTable>): Builder = apply { tables.forEach(::addTable) }

        /**
         * Registers a table using a [PgenWalEventTable] definition.
         *
         * The table is configured with FULL replica identity and its associated WAL
         * event mapper.
         *
         * @param table Table definition.
         */
        public fun addTable(table: PgenWalEventTable): Builder = apply {
            tableInfo[table.getTableNameWithSchema()] = TableInfo(
                replicaIdentity = ReplicaIdentity.Full,
                mapper = table::walEventMapper,
            )
        }

        /**
         * Registers a table by [TableNameWithSchema] with a custom replica identity and event mapper.
         *
         * @param table Table name including schema.
         * @param replicaIdentity Replica identity to apply to the table.
         * @param mapper Function mapping [WalEvent]s.
         */
        public fun addTable(
            table: TableNameWithSchema,
            replicaIdentity: ReplicaIdentity,
            mapper: (WalEvent.Change<JsonObject>) -> WalEvent.Change<*>,
        ): Builder = apply {
            tableInfo[table] = TableInfo(replicaIdentity = replicaIdentity, mapper = mapper)
        }

        /**
         * Registers multiple tables with a shared replica identity.
         *
         * @param mapper Map of table names to WAL mappers.
         * @param replicaIdentity Replica identity to apply to all tables.
         */
        public fun addTable(
            mapper: Map<TableNameWithSchema, (WalEvent<JsonObject>) -> WalEvent.Change<*>>,
            replicaIdentity: ReplicaIdentity,
        ): Builder = apply {
            val newEntries = mapper.mapValues { TableInfo(replicaIdentity = replicaIdentity, mapper = it.value) }
            tableInfo.putAll(newEntries)
        }

        internal fun build() = PgenWalEventListener(
            slot = slot,
            url = url,
            properties = properties,
            statusUpdateInterval = statusUpdateInterval,
            mutableSharedFlow = MutableSharedFlow(
                replay = flowReplay,
                extraBufferCapacity = flowExtraBufferCapacity,
                onBufferOverflow = flowOnBufferOverflow,
            ),
            tableInfo = tableInfo,
        )
    }

    private class TableInfo(
        val replicaIdentity: ReplicaIdentity,
        val mapper: (WalEvent.Change<JsonObject>) -> WalEvent.Change<*>
    )

    private companion object {
        private val log = LoggerFactory.getLogger(PgenWalEventListener::class.java)!!
        private const val SQL_STATE_DUPLICATE_OBJECT = "42710"
        private const val SQL_STATE_UNDEFINED_OBJECT = "42704"
        private const val SQL_STATE_QUERY_CANCELED = "57014"
        private const val WAKE_UP_MESSAGE_PREFIX = "de.quati.pgen.wal.wakeup"
        private const val PG_VERSION_EMIT_MESSAGE_FLUSH = 17
        private val STOP_TIMEOUT = 30.seconds

        private fun Connection.deleteSlot(name: String) =
            prepareStatement("select pg_drop_replication_slot(?)").use { stmt ->
                stmt.setString(1, name)
                try {
                    stmt.execute()
                } catch (e: PSQLException) {
                    if (e.sqlState != SQL_STATE_UNDEFINED_OBJECT)
                        throw e
                }
            }

        private fun Connection.createSlot(name: String) =
            prepareStatement("select pg_create_logical_replication_slot(?, 'wal2json')").use { stmt ->
                stmt.setString(1, name)
                try {
                    stmt.execute()
                } catch (e: PSQLException) {
                    if (e.sqlState != SQL_STATE_DUPLICATE_OBJECT)
                        throw e
                }
            }

        private fun Connection.setReplicaIdentities(data: Map<TableNameWithSchema, ReplicaIdentity>) =
            createStatement().use { stmt ->
                val sql = data.entries.joinToString(separator = "\n") { (table, replicaIdentity) ->
                    replicaIdentity.toSql(table)
                }
                @Suppress("SqlSourceToSinkFlow")
                stmt.execute(sql)
            }
    }
}