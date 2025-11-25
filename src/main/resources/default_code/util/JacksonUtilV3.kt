package default_code.util

import com.fasterxml.jackson.annotation.JsonFormat
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.Version
import tools.jackson.core.io.SerializedString
import tools.jackson.core.json.PackageVersion
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.deser.Deserializers
import tools.jackson.databind.deser.ValueInstantiator
import tools.jackson.databind.deser.std.ReferenceTypeDeserializer
import tools.jackson.databind.jsontype.TypeDeserializer
import tools.jackson.databind.jsontype.TypeSerializer
import tools.jackson.databind.ser.BeanPropertyWriter
import tools.jackson.databind.ser.Serializers
import tools.jackson.databind.ser.std.ReferenceTypeSerializer
import tools.jackson.databind.type.ReferenceType
import tools.jackson.databind.type.TypeBindings
import tools.jackson.databind.type.TypeFactory
import tools.jackson.databind.type.TypeModifier
import tools.jackson.databind.util.NameTransformer
import tools.jackson.databind.module.SimpleModule
import io.github.goquati.kotlin.util.Option
import io.github.goquati.kotlin.util.isSome
import io.github.goquati.kotlin.util.takeSome
import tools.jackson.databind.BeanDescription
import tools.jackson.databind.BeanProperty
import tools.jackson.databind.DeserializationConfig
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JavaType
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.PropertyName
import tools.jackson.databind.SerializationConfig
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.ser.ValueSerializerModifier
import tools.jackson.databind.ser.bean.UnwrappingBeanPropertyWriter
import java.lang.reflect.Type
import kotlin.reflect.KClass


interface QuatiJacksonStringSerializer<T : Any> {
    val clazz: KClass<T>
    val jSerializer: ValueSerializer<T>
    val jDeserializer: ValueDeserializer<T>
}

fun JsonMapper.Builder.addSimpleModule(block: SimpleModule.() -> Unit) =
    addModule(SimpleModule().apply(block))

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
        ser: ValueSerializer<Any>?
    ) : super(fullType, staticTyping, vts, ser)

    private constructor(
        base: OptionSerializer,
        property: BeanProperty?,
        vts: TypeSerializer?,
        valueSer: ValueSerializer<*>?,
        unwrapper: NameTransformer?,
        suppressableValue: Any?
    ) : super(base, property, vts, valueSer, unwrapper, suppressableValue, false)

    override fun withResolved(
        prop: BeanProperty?,
        vts: TypeSerializer?,
        valueSer: ValueSerializer<*>?,
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
    deser: ValueDeserializer<*>?
) : ReferenceTypeDeserializer<Option<*>>(fullType, inst, typeDeser, deser) {
    private val isStringDeserializer: Boolean = (fullType is ReferenceType
            && fullType.referencedType != null
            && fullType.referencedType.isTypeOrSubTypeOf(String::class.java))

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Option<*> {
        val t = p.currentToken()
        if (t == JsonToken.VALUE_STRING && !isStringDeserializer && p.string.trim().isEmpty())
            return Option.Undefined
        return super.deserialize(p, ctxt)
    }

    override fun withResolved(
        typeDeser: TypeDeserializer?,
        valueDeser: ValueDeserializer<*>?
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
    override fun serializeAsProperty(bean: Any?, g: JsonGenerator?, ctxt: SerializationContext?) {
        val value = get(bean)
        if (value == Option.Undefined || (_nullSerializer == null && value == null))
            return
        super.serializeAsProperty(bean, g, ctxt)
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

    override fun serializeAsProperty(bean: Any?, gen: JsonGenerator?, prov: SerializationContext?) {
        val value = get(bean)
        if (Option.Undefined == value || (_nullSerializer == null && value == null))
            return
        super.serializeAsProperty(bean, gen, prov)
    }
}


object OptionSerializers : Serializers.Base() {
    override fun findReferenceSerializer(
        config: SerializationConfig,
        type: ReferenceType,
        beanDescRef: BeanDescription.Supplier?,
        formatOverrides: JsonFormat.Value?,
        contentTypeSerializer: TypeSerializer?,
        contentValueSerializer: ValueSerializer<in Any>?
    ): ValueSerializer<*>? = if (Option::class.java.isAssignableFrom(type.rawClass)) {
        val staticTyping = contentTypeSerializer == null && config.isEnabled(MapperFeature.USE_STATIC_TYPING)
        OptionSerializer(
            type, staticTyping,
            contentTypeSerializer, contentValueSerializer
        )
    } else null
}

object OptionDeserializers : Deserializers.Base() {
    override fun findReferenceDeserializer(
        refType: ReferenceType,
        config: DeserializationConfig?,
        beanDescRef: BeanDescription.Supplier?,
        contentTypeDeserializer: TypeDeserializer?,
        contentDeserializer: ValueDeserializer<*>?
    ): ValueDeserializer<*>? = if (refType.hasRawClass(Option::class.java)) OptionDeserializer(
        refType,
        null,
        contentTypeDeserializer,
        contentDeserializer
    ) else null

    override fun hasDeserializerFor(
        config: DeserializationConfig?,
        valueType: Class<*>?
    ): Boolean = valueType?.isAssignableFrom(Option::class.java) ?: false
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

class OptionValueSerializerModifier : ValueSerializerModifier() {
    override fun changeProperties(
        config: SerializationConfig,
        beanDesc: BeanDescription.Supplier,
        beanProperties: MutableList<BeanPropertyWriter>
    ): MutableList<BeanPropertyWriter> {
        for (i in beanProperties.indices) {
            val writer = beanProperties[i]
            val type = writer.type
            if (type.isTypeOrSubTypeOf(Option::class.java)) {
                beanProperties[i] = OptionBeanPropertyWriter(writer)
            }
        }
        return beanProperties
    }
}

class QuatiOptionModule() : SimpleModule() {
    companion object {
        private const val NAME: String = "QuatiOptionModule"
    }

    override fun version(): Version? = PackageVersion.VERSION
    override fun getModuleName() = NAME
    override fun setupModule(context: SetupContext) {
        context.addSerializers(OptionSerializers)
        context.addDeserializers(OptionDeserializers)
        context.addTypeModifier(OptionTypeModifier)
        context.addSerializerModifier(OptionValueSerializerModifier())
    }
}