package de.quati.pgen.core.column

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.Table
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

public class DomainColumnType<T : Any, R>(
    kClass: KClass<T>,
    public val sqlType: String,
    public val originType: IColumnType<R>,
    public val builder: (R?) -> T,
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
    override fun valueFromDB(value: Any): T {
        val innerValue = originType.valueFromDB(value)
        return builder(innerValue)
    }
}


public inline fun <reified T : Any, R> Table.domainType(
    name: String,
    sqlType: String,
    originType: IColumnType<R>,
    noinline builder: (R?) -> T,
): Column<T> {
    val type = DomainColumnType(
        kClass = T::class,
        sqlType = sqlType,
        originType = originType,
        builder = builder,
    )
    return registerColumn(name = name, type = type)
}