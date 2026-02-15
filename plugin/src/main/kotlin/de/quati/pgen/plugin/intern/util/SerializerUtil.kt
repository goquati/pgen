package de.quati.pgen.plugin.intern.util

import de.quati.pgen.plugin.intern.model.spec.SqlObjectName
import de.quati.pgen.plugin.intern.model.spec.Column
import de.quati.pgen.plugin.intern.model.spec.SqlType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

internal object ColumnTypeSerializer : KSerializer<Column.Type> {
    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        ColumnTypeSerializer::class.java.name,
        SerialKind.CONTEXTUAL
    ) {
        element<String>("primitive")
        element<Column.Type.NonPrimitive>("nonPrimitive")
    }

    override fun serialize(encoder: Encoder, value: Column.Type) {
        when (value) {
            is Column.Type.Primitive -> encoder.encodeSerializableValue(SqlType.serializer(), value.sqlType)
            is Column.Type.Reference -> encoder.encodeSerializableValue(SqlType.serializer(), value.sqlType)
            is Column.Type.CustomType -> serialize(encoder, value.toRef())
            is Column.Type.Overwrite -> throw SerializationException("Cannot serialize 'Overwrite' type")
            is Column.Type.NonPrimitive ->
                encoder.encodeSerializableValue(Column.Type.NonPrimitive.serializer(), value)
        }
    }

    override fun deserialize(decoder: Decoder): Column.Type {
        val decoder = decoder as? JsonDecoder
            ?: throw SerializationException("This serializer can only be used with JSON")
        return when (val node = decoder.decodeJsonElement()) {
            is JsonObject -> decoder.json.decodeFromJsonElement<Column.Type.NonPrimitive>(node)
            is JsonPrimitive -> {
                val type = SqlType.parse(node.content)
                Column.Type.Primitive.entries.firstOrNull { it.sqlType == type }
                    ?: node.content.let(SqlObjectName::parse).let(Column.Type::Reference)
            }

            else -> throw SerializationException("Invalid JSON for Column.Type")
        }
    }
}

internal object SqlObjectNameSerializer : KSerializer<SqlObjectName> {
    @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        SqlObjectNameSerializer::class.java.name,
        SerialKind.CONTEXTUAL
    ) {
        element<String>("string")
    }

    override fun serialize(encoder: Encoder, value: SqlObjectName) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): SqlObjectName {
        return decoder.decodeString().let(SqlObjectName::parse)
    }
}
