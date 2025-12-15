package de.quati.pgen.jdbc.column

import kotlinx.datetime.DateTimePeriod
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.postgresql.util.PGInterval
import org.postgresql.util.PGobject

public class IntervalColumnType : ColumnType<DateTimePeriod>() {
    override fun sqlType(): String = "INTERVAL"
    override fun nonNullValueToString(value: DateTimePeriod): String = "'${value}'"
    override fun notNullValueToDB(value: DateTimePeriod): Any = value.toString()
    override fun valueFromDB(value: Any): DateTimePeriod = when (value) {
        is PGInterval ->  DateTimePeriod(
                years = value.years,
                months = value.months,
                days = value.days,
                hours = value.hours,
                minutes = value.minutes,
                seconds = value.wholeSeconds,
                nanoseconds = value.microSeconds.toLong() * 1000,
            )
        else -> error("Unexpected value of type DateTimePeriod")
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

public fun Table.interval(name: String): Column<DateTimePeriod> = registerColumn(name, IntervalColumnType())
