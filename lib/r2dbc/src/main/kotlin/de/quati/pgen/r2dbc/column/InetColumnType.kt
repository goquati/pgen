package de.quati.pgen.r2dbc.column

import de.quati.pgen.core.column.IpColumnType
import inet.ipaddr.IPAddress
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table

public class InetColumnType : IpColumnType() {
    override fun sqlType(): String = "inet"
}

public fun Table.inet(name: String): Column<IPAddress> {
    return registerColumn(name = name, type = InetColumnType())
}