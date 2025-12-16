package de.quati.pgen.tests.jdbc.basic.shared

import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject
import java.net.InetAddress

class InetColumnType : ColumnType<IPAddress>() {
    override fun sqlType(): String = "inet"
    override fun notNullValueToDB(value: IPAddress): Any = value.toString()
    override fun valueFromDB(value: Any): IPAddress = when (value) {
        is PGobject -> value.value!!
        is InetAddress -> value.hostAddress
        is String -> value
        else -> value.toString()
    }.let { IPAddressString(it).toAddress() }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val parameterValue: PGobject? = value?.let {
            PGobject().apply {
                type = sqlType()
                this.value = value as? String
            }
        }
        super.setParameter(stmt, index, parameterValue)
    }
}