package de.quati.pgen.plugin.intern.dsl

import org.intellij.lang.annotations.Language
import java.sql.Connection
import java.sql.ResultSet


fun Connection.execute(@Language("sql") query: String) {
    createStatement().use { statement -> statement.execute(query) }
}

fun <T> Connection.executeQuery(@Language("sql") query: String, mapper: (ResultSet) -> T): List<T> {
    return createStatement().use { statement ->
        statement.executeQuery(query).use { rs ->
            buildList { while (rs.next()) add(mapper(rs)) }
        }
    }
}
