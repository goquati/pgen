package de.quati.pgen.core.column

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

public class DomainColumnType<T : Any>(
    kClass: KClass<T>,
    private val sqlType: String,
    private val builder: (Any) -> T,
) : ColumnType<T>() {
    private val getter: KProperty1<T, Any>

    init {
        val prop = kClass.memberProperties.singleOrNull() ?: run {
            val paramName = kClass.primaryConstructor
                .let { it ?: error("$kClass has no primary constructor") }
                .parameters.singleOrNull()?.name
                ?: error("$kClass does not have exactly one constructor parameter")

            kClass.memberProperties
                .singleOrNull { it.name == paramName }
                .let { it ?: error("property '$paramName' not found in $kClass") }
        }

        getter = prop.also { require(!it.returnType.isMarkedNullable) { "property of $kClass is nullable" } }
            .let { @Suppress("UNCHECKED_CAST") (it as KProperty1<T, Any>) }
    }

    override fun sqlType(): String = sqlType
    override fun notNullValueToDB(value: T): Any = getter.get(value)
    override fun nonNullValueToString(value: T): String = "'$value'"
    override fun valueFromDB(value: Any): T = builder(value)
}


public inline fun <reified T : Any> Table.domainType(
    name: String,
    sqlType: String,
    noinline builder: (Any) -> T,
): Column<T> {
    val type = DomainColumnType(kClass = T::class, sqlType = sqlType, builder = builder)
    return registerColumn(name = name, type = type)
}