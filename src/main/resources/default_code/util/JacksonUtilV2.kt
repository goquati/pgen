package default_code.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.core.io.SerializedString
import com.fasterxml.jackson.core.json.PackageVersion
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.deser.Deserializers
import com.fasterxml.jackson.databind.deser.ValueInstantiator
import com.fasterxml.jackson.databind.deser.std.ReferenceTypeDeserializer
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer
import com.fasterxml.jackson.databind.jsontype.TypeSerializer
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier
import com.fasterxml.jackson.databind.ser.Serializers
import com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter
import com.fasterxml.jackson.databind.ser.std.ReferenceTypeSerializer
import com.fasterxml.jackson.databind.type.ReferenceType
import com.fasterxml.jackson.databind.type.TypeBindings
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.databind.type.TypeModifier
import com.fasterxml.jackson.databind.util.NameTransformer
import com.fasterxml.jackson.databind.module.SimpleModule
import io.github.goquati.kotlin.util.Option
import io.github.goquati.kotlin.util.isSome
import io.github.goquati.kotlin.util.takeSome
import java.lang.reflect.Type
import kotlin.reflect.KClass


interface QuatiJacksonStringSerializer<T : Any> {
    val clazz: KClass<T>
    val jSerializer: JsonSerializer<T>
    val jDeserializer: JsonDeserializer<T>
}

fun ObjectMapper.registerSimpleModule(block: SimpleModule.() -> Unit) =
    registerModule(SimpleModule().apply(block))

fun <T : Any> SimpleModule.add(serializer: QuatiJacksonStringSerializer<T>) {
    addDeserializer(serializer.clazz.java, serializer.jDeserializer)
    addSerializer(serializer.clazz.java, serializer.jSerializer)
}

fun SimpleModule.add(serializers: Iterable<QuatiJacksonStringSerializer<*>>) {
    serializers.forEach { add(it) }
}

class OptionSerializer : ReferenceTypeSerializer<Option<*>> {
    constructor(
        fullType: ReferenceType,
        staticTyping: Boolean,
        vts: TypeSerializer?,
        ser: JsonSerializer<Any>?
    ) : super(fullType, staticTyping, vts, ser)

    private constructor(
        base: OptionSerializer,
        property: BeanProperty?,
        vts: TypeSerializer?,
        valueSer: JsonSerializer<*>?,
        unwrapper: NameTransformer?,
        suppressableValue: Any?
    ) : super(base, property, vts, valueSer, unwrapper, suppressableValue, false)

    override fun withResolved(
        prop: BeanProperty?,
        vts: TypeSerializer?,
        valueSer: JsonSerializer<*>?,
        unwrapper: NameTransformer?
    ): ReferenceTypeSerializer<Option<*>> =
        OptionSerializer(this, prop, vts, valueSer, unwrapper, _suppressableValue)

    override fun withContentInclusion(
        suppressableValue: Any?,
        suppressNulls: Boolean,
    ): ReferenceTypeSerializer<Option<*>> =
        OptionSerializer(this, _property, _valueTypeSerializer, _valueSerializer, _unwrapper, suppressableValue)

    override fun _isValuePresent(value: Option<*>): Boolean = value.isSome
    override fun _getReferenced(value: Option<*>): Any? = value.takeSome()?.value
    override fun _getReferencedIfPresent(value: Option<*>): Any? = value.takeSome()?.value
}

class OptionDeserializer(
    fullType: JavaType,
    inst: ValueInstantiator?,
    typeDeser: TypeDeserializer?,
    deser: JsonDeserializer<*>?
) : ReferenceTypeDeserializer<Option<*>>(fullType, inst, typeDeser, deser) {
    private val isStringDeserializer: Boolean = (fullType is ReferenceType
            && fullType.referencedType != null
            && fullType.referencedType.isTypeOrSubTypeOf(String::class.java))

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Option<*> {
        val t = p.currentToken
        if (t == JsonToken.VALUE_STRING && !isStringDeserializer && p.text.trim().isEmpty())
            return Option.Undefined
        return super.deserialize(p, ctxt)
    }

    override fun withResolved(
        typeDeser: TypeDeserializer?,
        valueDeser: JsonDeserializer<*>?
    ) = OptionDeserializer(_fullType, _valueInstantiator, typeDeser, valueDeser)

    override fun getAbsentValue(ctxt: DeserializationContext) = Option.Undefined
    override fun getNullValue(ctxt: DeserializationContext) = Option.Some(null)
    override fun getEmptyValue(ctxt: DeserializationContext) = Option.Undefined
    override fun referenceValue(contents: Any?) = Option.Some(contents)
    override fun updateReference(reference: Option<*>, contents: Any?) = Option.Some(contents)
    override fun supportsUpdate(config: DeserializationConfig): Boolean = true
    override fun getReferenced(reference: Option<*>) = reference.takeSome()?.value
}

class OptionBeanPropertyWriter : BeanPropertyWriter {
    constructor(base: BeanPropertyWriter) : super(base)
    constructor(base: BeanPropertyWriter, newName: PropertyName) : super(base, newName)

    override fun _new(newName: PropertyName) = OptionBeanPropertyWriter(this, newName)
    override fun unwrappingWriter(unwrapper: NameTransformer?) = UnwrappingOptionBeanPropertyWriter(this, unwrapper)
    override fun serializeAsField(bean: Any?, jgen: JsonGenerator?, prov: SerializerProvider?) {
        val value = get(bean)
        if (value == Option.Undefined || (_nullSerializer == null && value == null))
            return
        super.serializeAsField(bean, jgen, prov)
    }
}

class UnwrappingOptionBeanPropertyWriter : UnwrappingBeanPropertyWriter {
    constructor(
        base: BeanPropertyWriter,
        transformer: NameTransformer?
    ) : super(base, transformer)

    constructor(
        base: UnwrappingBeanPropertyWriter,
        transformer: NameTransformer?, name: SerializedString?
    ) : super(base, transformer, name)

    override fun _new(transformer: NameTransformer?, newName: SerializedString?) =
        UnwrappingOptionBeanPropertyWriter(this, transformer, newName)

    override fun serializeAsField(bean: Any?, gen: JsonGenerator?, prov: SerializerProvider?) {
        val value = get(bean)
        if (Option.Undefined == value || (_nullSerializer == null && value == null))
            return
        super.serializeAsField(bean, gen, prov)
    }
}


object OptionSerializers : Serializers.Base() {
    override fun findReferenceSerializer(
        config: SerializationConfig,
        refType: ReferenceType, beanDesc: BeanDescription?,
        contentTypeSerializer: TypeSerializer?,
        contentValueSerializer: JsonSerializer<Any>?,
    ): JsonSerializer<*>? = if (Option::class.java.isAssignableFrom(refType.rawClass)) {
        val staticTyping = contentTypeSerializer == null && config.isEnabled(MapperFeature.USE_STATIC_TYPING)
        OptionSerializer(
            refType, staticTyping,
            contentTypeSerializer, contentValueSerializer
        )
    } else null
}

object OptionDeserializers : Deserializers.Base() {
    override fun findReferenceDeserializer(
        refType: ReferenceType,
        config: DeserializationConfig?, beanDesc: BeanDescription?,
        contentTypeDeserializer: TypeDeserializer?, contentDeserializer: JsonDeserializer<*>?
    ): JsonDeserializer<*>? = if (refType.hasRawClass(Option::class.java)) OptionDeserializer(
        refType,
        null,
        contentTypeDeserializer,
        contentDeserializer
    ) else null
}

object OptionTypeModifier : TypeModifier() {
    override fun modifyType(
        type: JavaType,
        jdkType: Type?,
        bindings: TypeBindings?,
        typeFactory: TypeFactory?,
    ): JavaType? = when {
        type.isReferenceType || type.isContainerType -> type
        type.rawClass == Option::class.java -> {
            val refType = type.containedTypeOrUnknown(0)
            ReferenceType.upgradeFrom(type, refType)
        }

        else -> type
    }
}

object OptionBeanSerializerModifier : BeanSerializerModifier() {
    override fun changeProperties(
        config: SerializationConfig?,
        beanDesc: BeanDescription?,
        beanProperties: MutableList<BeanPropertyWriter>
    ): MutableList<BeanPropertyWriter> {
        for (i in beanProperties.indices) {
            val writer = beanProperties[i]
            val type = writer.type
            if (type.isTypeOrSubTypeOf(Option::class.java))
                beanProperties[i] = OptionBeanPropertyWriter(writer)
        }
        return beanProperties
    }
}

data object QuatiOptionModule : Module() {
    private const val NAME: String = "QuatiOptionModule"
    override fun version(): Version? = PackageVersion.VERSION
    override fun getModuleName() = NAME
    override fun setupModule(context: SetupContext) {
        context.addSerializers(OptionSerializers)
        context.addDeserializers(OptionDeserializers)
        context.addTypeModifier(OptionTypeModifier)
        context.addBeanSerializerModifier(OptionBeanSerializerModifier)
    }
}