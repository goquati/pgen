package de.quati.pgen.r2dbc.column

import io.r2dbc.postgresql.codec.Interval
import kotlinx.datetime.DateTimePeriod
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table

public class IntervalColumnType : ColumnType<DateTimePeriod>() {
    override fun sqlType(): String = "INTERVAL"
    override fun nonNullValueToString(value: DateTimePeriod): String = "'${value}'"
    override fun notNullValueToDB(value: DateTimePeriod): Any = value.toString()
    override fun valueFromDB(value: Any): DateTimePeriod = when (value) {
       is Interval -> DateTimePeriod(
           years = value.years,
           months = value.months,
           days = value.days,
           hours = value.hours,
           minutes = value.minutes,
           seconds = value.secondsInMinute,
           nanoseconds = value.microsecondsInSecond.toLong() * 1000,
       )
        else -> error("Unexpected value of type DateTimePeriod")
    }
}

public fun Table.interval(name: String): Column<DateTimePeriod> = registerColumn(name, IntervalColumnType())
