package de.quati.pgen.tests.r2dbc.basic.shared

import de.quati.pgen.core.column.PgenColumnType
import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import java.net.InetAddress

class InetColumnType : PgenColumnType<IPAddress>() {
    override fun sqlType(): String = "inet"
    override fun notNullValueToDB(value: IPAddress): Any = value.toString()
    override fun valueFromDB(value: Any): IPAddress = when (value) {
        is InetAddress -> value.hostAddress
        is String -> value
        else -> value.toString()
    }.let { IPAddressString(it).toAddress() }
}