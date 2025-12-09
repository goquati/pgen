package de.quati.pgen.shared

public interface IConnectionProperties {
    public val url: String
    public val username: String
    public val password: String

    public fun toConnectionConfig(): ConnectionConfig = ConnectionConfig.parse(url)
}
