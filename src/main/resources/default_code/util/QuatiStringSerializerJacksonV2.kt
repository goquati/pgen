package default_code.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.KClass


abstract class QuatiStringSerializer<T : Any>(
    override val clazz: KClass<T>,
    val name: String = clazz.simpleName!! + "Serializer",
    val serialize: (T) -> String,
    val deserialize: (String) -> T,
) : QuatiKotlinxStringSerializer<T>, QuatiJacksonStringSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    override val jSerializer: JsonSerializer<T> = object : JsonSerializer<T>() {
        override fun serialize(value: T, gen: JsonGenerator, serializer: SerializerProvider) {
            val sValue = serialize(value)
            gen.writeString(sValue)
        }
    }
    override val jDeserializer: JsonDeserializer<T> = object : JsonDeserializer<T>() {
        override fun deserialize(parser: JsonParser, context: DeserializationContext): T? {
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