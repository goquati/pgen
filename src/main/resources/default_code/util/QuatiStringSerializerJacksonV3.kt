package default_code.util

import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ValueSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tools.jackson.databind.SerializationContext
import kotlin.reflect.KClass


abstract class QuatiStringSerializer<T : Any>(
    override val clazz: KClass<T>,
    val name: String = clazz.simpleName!! + "Serializer",
    val serialize: (T) -> String,
    val deserialize: (String) -> T,
) : QuatiKotlinxStringSerializer<T>, QuatiJacksonStringSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    override val jSerializer: ValueSerializer<T> = object : ValueSerializer<T>() {
        override fun serialize(value: T, gen: JsonGenerator, ctxt: SerializationContext) {
            val sValue = serialize(value)
            gen.writeString(sValue)
        }
    }
    override val jDeserializer: ValueDeserializer<T> = object : ValueDeserializer<T>() {
        override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): T? {
            val sValue = parser.valueAsString ?: return null
            return deserialize(sValue)
        }
    }

    override fun serialize(encoder: Encoder, value: T) {
        val sValue = serialize(value)
        return encoder.encodeString(sValue)
    }

    override fun deserialize(decoder: Decoder): T {
        val sValue = decoder.decodeString()
        return deserialize(sValue)
    }
}