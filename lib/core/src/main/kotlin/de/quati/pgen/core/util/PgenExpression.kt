package de.quati.pgen.core.util

import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.QueryBuilder


public data class PgenExpression <T>(val expr: String): Expression<T>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append(expr)
    }
}
