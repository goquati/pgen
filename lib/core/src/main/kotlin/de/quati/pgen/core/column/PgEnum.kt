package de.quati.pgen.core.column

import kotlin.enums.enumEntries
import kotlin.reflect.KClass

public interface PgEnum {
    public val pgEnumTypeName: String
    public val pgEnumLabel: String
}

public inline fun <reified T> getPgEnumByLabel(label: String): T
        where T : Enum<T>,
              T : PgEnum {
    return enumEntries<T>().singleOrNull { e -> e.pgEnumLabel == label }
        ?: error("enum with label '$label' not found in '${T::class.qualifiedName}'")
}

public fun <T> getPgEnumByLabel(clazz: KClass<T>, label: String): T
        where T : Enum<T>,
              T : PgEnum {
    return clazz.java.enumConstants.singleOrNull { e -> e.pgEnumLabel == label }
        ?: error("enum with label '$label' not found in '${clazz.qualifiedName}'")
}