package de.quati.pgen.core.column

import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import org.jetbrains.exposed.v1.core.ColumnType
import java.net.InetAddress


public abstract class IpColumnType : ColumnType<IPAddress>() {
    override fun notNullValueToDB(value: IPAddress): Any = value.toString()
    override fun valueFromDB(value: Any): IPAddress = when (value) {
        is InetAddress -> IPAddressString(value.hostAddress).toAddress()
        is String -> IPAddressString(value).toAddress()
        else -> IPAddressString(value.toString()).toAddress()
    }
}

