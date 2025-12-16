package de.quati.pgen.r2dbc.column

import de.quati.pgen.core.column.IpColumnType
import inet.ipaddr.IPAddress
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table

public class CidrColumnType : IpColumnType() {
    override fun sqlType(): String = "cidr"
}

public fun Table.cidr(name: String): Column<IPAddress> {
    return registerColumn(name = name, type = CidrColumnType())
}