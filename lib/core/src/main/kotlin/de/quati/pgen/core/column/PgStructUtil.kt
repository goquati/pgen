package de.quati.pgen.core.column

import de.quati.pgen.shared.PgenEnum
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi

@JvmInline
public value class PgStructField(public val data: String?) {
    public companion object {
        private val SPLIT_REGEX = Regex(""",(?![^"]*"(?:(?:[^"]*"){2})*[^"]*$)""")
        public fun parseFields(data: String): List<PgStructField> {
            return data.trim('(', ')').split(SPLIT_REGEX).map(::PgStructField)
        }
    }
}

public fun List<PgStructField>.join(): String = joinToString(
    separator = ",",
    prefix = "(",
    postfix = ")",
) { it.data ?: "" }

public interface PgStructFieldConverter<T> {
    public fun serialize(obj: T?): PgStructField
    public fun deserialize(obj: PgStructField): T?

    public object String : PgStructFieldConverter<kotlin.String> {
        override fun serialize(obj: kotlin.String?): PgStructField = PgStructField(obj?.escapeString())
        override fun deserialize(obj: PgStructField): kotlin.String? = obj.data?.unescapeString()
    }

    public object Short : PgStructFieldConverter<kotlin.Short> {
        override fun serialize(obj: kotlin.Short?): PgStructField = PgStructField(obj?.toString())
        override fun deserialize(obj: PgStructField): kotlin.Short? = obj.data?.toShortOrNull()
    }

    public object Int : PgStructFieldConverter<kotlin.Int> {
        override fun serialize(obj: kotlin.Int?): PgStructField = PgStructField(obj?.toString())
        override fun deserialize(obj: PgStructField): kotlin.Int? = obj.data?.toIntOrNull()
    }

    public object Long : PgStructFieldConverter<kotlin.Long> {
        override fun serialize(obj: kotlin.Long?): PgStructField = PgStructField(obj?.toString())
        override fun deserialize(obj: PgStructField): kotlin.Long? = obj.data?.toLongOrNull()
    }

    public object Float : PgStructFieldConverter<kotlin.Float> {
        override fun serialize(obj: kotlin.Float?): PgStructField = PgStructField(obj?.toString())
        override fun deserialize(obj: PgStructField): kotlin.Float? = obj.data?.toFloatOrNull()
    }

    public object Double : PgStructFieldConverter<kotlin.Double> {
        override fun serialize(obj: kotlin.Double?): PgStructField = PgStructField(obj?.toString())
        override fun deserialize(obj: PgStructField): kotlin.Double? = obj.data?.toDoubleOrNull()
    }

    @OptIn(ExperimentalStdlibApi::class)
    public object ByteArray : PgStructFieldConverter<kotlin.ByteArray> {
        override fun serialize(obj: kotlin.ByteArray?): PgStructField = obj?.toHexString()
            ?.let { "\\x$it".escapeString() }.let(::PgStructField)

        override fun deserialize(obj: PgStructField): kotlin.ByteArray? =
            obj.data?.unescapeString()?.removePrefix("\\x")?.hexToByteArray()
    }

    @OptIn(ExperimentalUuidApi::class)
    public object Uuid : PgStructFieldConverter<kotlin.uuid.Uuid> {
        override fun serialize(obj: kotlin.uuid.Uuid?): PgStructField = PgStructField(obj?.toString()?.escapeString())
        override fun deserialize(obj: PgStructField): kotlin.uuid.Uuid? = obj.data?.let { kotlin.uuid.Uuid.parse(it) }
    }

    public object BigDecimal : PgStructFieldConverter<java.math.BigDecimal> {
        override fun serialize(obj: java.math.BigDecimal?): PgStructField = PgStructField(obj?.toString())
        override fun deserialize(obj: PgStructField): java.math.BigDecimal? = obj.data?.toBigDecimalOrNull()
    }

    public class Enum<E>(
        private val clazz: KClass<E>
    ) : PgStructFieldConverter<E> where E : PgenEnum, E : kotlin.Enum<E> {
        override fun serialize(obj: E?): PgStructField = PgStructField(obj?.pgenEnumLabel?.escapeString())
        override fun deserialize(obj: PgStructField): E? = obj.data?.unescapeString()
            ?.let { PgenEnum.getByLabel(clazz, it) }
    }

    public companion object {
        private fun kotlin.String.escapeString() = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
        private fun kotlin.String.unescapeString() = replace("\\\\", "\\").replace("\\\"", "\"").let {
            if (it.startsWith('"') && it.endsWith('"')) it.substring(1, it.length - 1)
            else it
        }
    }
}