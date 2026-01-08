package de.quati.pgen.wal


import de.quati.pgen.core.util.pgenTimestampTzFormatter
import de.quati.pgen.shared.TableNameWithSchema
import de.quati.pgen.shared.WalEvent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.OffsetDateTime


@Serializable
internal sealed interface WalEventIntern {
    companion object Companion {
        private val json = Json {
            classDiscriminator = "action"
            ignoreUnknownKeys = true
        }

        fun parse(data: ByteArray) = parse(data.decodeToString())
        fun parse(jsonString: String) = json.decodeFromString<WalEventIntern>(jsonString)
    }


    sealed interface Info : WalEventIntern {
        val timestamp: OffsetDateTime?
        fun toEvent(): WalEvent.Message {
            val metaData = WalEvent.MetaData(timestamp = timestamp ?: OffsetDateTime.now())
            return when (this) {
                is Message -> WalEvent.Message(
                    metaData = metaData,
                    transactional = transactional,
                    prefix = prefix,
                    content = content,
                )
            }
        }
    }

    sealed interface Change : WalEventIntern {
        val timestamp: OffsetDateTime
        val schema: String
        val table: String
        val tableNameWithSchema get() = TableNameWithSchema(schema = schema, table = table)
        fun toEvent(
            mapper: (WalEvent.Change<JsonObject>) -> WalEvent.Change<*>,
        ): WalEvent.Change<*> {
            val metaData = WalEvent.MetaData(
                timestamp = timestamp,
            )
            val payload = when (this) {
                is Delete -> WalEvent.Change.Payload.Delete(dataOld = dataOld)
                is Insert -> WalEvent.Change.Payload.Insert(dataNew = dataNew)
                is Truncate -> WalEvent.Change.Payload.Truncate
                is Update -> WalEvent.Change.Payload.Update(dataOld = dataOld, dataNew = dataNew)
            }
            val base = WalEvent.Change.Base(
                table = tableNameWithSchema,
                metaData = metaData,
                payload = payload,
            )
            return mapper(base)
        }
    }

    @Serializable
    @SerialName("M")
    data class Message(
        @Serializable(with = TimestampSerializer::class) override val timestamp: OffsetDateTime?,
        val transactional: Boolean,
        val prefix: String,
        val content: String,
    ) : Info

    @Serializable
    @SerialName("D")
    data class Delete(
        @Serializable(with = TimestampSerializer::class) override val timestamp: OffsetDateTime,
        override val schema: String,
        override val table: String,
        val identity: List<Field>,
    ) : Change {
        val dataOld get(): JsonObject = identity.associate { it.name to it.value }.let(::JsonObject)
    }

    @Serializable
    @SerialName("I")
    data class Insert(
        @Serializable(with = TimestampSerializer::class) override val timestamp: OffsetDateTime,
        override val schema: String,
        override val table: String,
        val columns: List<Field>,
    ) : Change {
        val dataNew get(): JsonObject = columns.associate { it.name to it.value }.let(::JsonObject)
    }

    @Serializable
    @SerialName("U")
    data class Update(
        @Serializable(with = TimestampSerializer::class) override val timestamp: OffsetDateTime,
        override val schema: String,
        override val table: String,
        val identity: List<Field>,
        val columns: List<Field>,
    ) : Change {
        val dataOld get(): JsonObject = identity.associate { it.name to it.value }.let(::JsonObject)
        val dataNew get(): JsonObject = columns.associate { it.name to it.value }.let(::JsonObject)
    }

    @Serializable
    @SerialName("T")
    data class Truncate(
        @Serializable(with = TimestampSerializer::class) override val timestamp: OffsetDateTime,
        override val schema: String,
        override val table: String,
    ) : Change

    @Serializable
    data class Field(val name: String, val value: JsonElement)

    object TimestampSerializer : KSerializer<OffsetDateTime> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)
        override fun serialize(encoder: Encoder, value: OffsetDateTime) =
            encoder.encodeString(pgenTimestampTzFormatter.format(value))

        override fun deserialize(decoder: Decoder): OffsetDateTime =
            OffsetDateTime.parse(decoder.decodeString(), pgenTimestampTzFormatter)
    }
}