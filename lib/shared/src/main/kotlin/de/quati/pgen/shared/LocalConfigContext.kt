package de.quati.pgen.shared

public interface ILocalConfigContext {
    public val data: Map<String, String>
}

@JvmInline
public value class LocalConfigContext(override val data: Map<String, String>) : ILocalConfigContext