package de.quati.pgen.jdbc.column

import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject

/** Reads and writes any postgres type via its text representation. */
public class TextBackedColumnType(private val sqlType: String) : TextColumnType() {
    override fun sqlType(): String = sqlType
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
