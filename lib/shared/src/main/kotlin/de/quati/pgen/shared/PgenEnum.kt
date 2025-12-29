package de.quati.pgen.shared

import kotlin.reflect.KClass

public interface PgenEnum {
    public val pgenEnumTypeName: String
    public val pgenEnumLabel: String

    public companion object Companion {
        public fun <T> getByLabel(clazz: KClass<T>, label: String): T
                where T : Enum<T>,
                      T : PgenEnum {
            return clazz.java.enumConstants.singleOrNull { e -> e.pgenEnumLabel == label }
                ?: error("enum with label '$label' not found in '${clazz.qualifiedName}'")
        }
    }
}
