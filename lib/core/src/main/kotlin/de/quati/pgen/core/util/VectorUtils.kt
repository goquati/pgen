package de.quati.pgen.core.util

import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.append


private class CosineDistanceOp<T : FloatArray?, S : FloatArray?>(
    val expr1: Expression<T>,
    val expr2: Expression<S>,
) : ExpressionWithColumnType<Double>() {
    override val columnType = DoubleColumnType()
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder { append('(', expr1, "::vector <=> ", expr2, "::vector)") }
    }
}

private class CosineSimilarityOp<T : FloatArray?, S : FloatArray?>(
    val expr1: Expression<T>,
    val expr2: Expression<S>,
) : ExpressionWithColumnType<Double>() {
    override val columnType = DoubleColumnType()
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder { append("(1 - (", expr1, "::vector <=> ", expr2, "::vector))") }
    }
}

public infix fun <T : FloatArray?> ExpressionWithColumnType<T>.cosineDistance(other: T): Expression<Double> =
    CosineDistanceOp(this, QueryParameter(other, columnType))

public infix fun <T : FloatArray?> ExpressionWithColumnType<T>.cosineSimilarity(other: T): Expression<Double> =
    CosineSimilarityOp(this, QueryParameter(other, columnType))
