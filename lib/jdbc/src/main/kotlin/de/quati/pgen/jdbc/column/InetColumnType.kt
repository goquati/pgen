package de.quati.pgen.jdbc.column

import de.quati.pgen.core.column.IpColumnType
import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject

public class InetColumnType : IpColumnType() {
    override fun sqlType(): String = "inet"
    override fun valueFromDB(value: Any): IPAddress = when (value) {
        is PGobject -> super.valueFromDB(value.value!!)
        else -> super.valueFromDB(value)
    }

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

public fun Table.inet(name: String): Column<IPAddress> {
    return registerColumn(name = name, type = InetColumnType())
}