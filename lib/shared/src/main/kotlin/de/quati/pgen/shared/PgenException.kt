package de.quati.pgen.shared


public sealed class PgenException(
    public val msg: String,
    throwable: Throwable? = null,
) : RuntimeException(msg, throwable) {
    public companion object Companion {
        public fun of(details: PgenErrorDetails, t: Throwable? = null): PgenException {
            val code = details.code.uppercase()
            val msg = details.message
            return when (code) {
                "23000" -> IntegrityConstraintViolation(details = details, msg = msg, t = t)
                "23001" -> RestrictViolation(details = details, msg = msg, t = t)
                "23502" -> NotNullViolation(details = details, msg = msg, t = t)
                "23503" -> ForeignKeyViolation(details = details, msg = msg, t = t)
                "23505" -> UniqueViolation(details = details, msg = msg, t = t)
                "23514" -> CheckViolation(details = details, msg = msg, t = t)
                "23P01" -> ExclusionViolation(details = details, msg = msg, t = t)
                else -> Generic(details = details, msg = msg, t = t)
            }
        }
    }

    public sealed class Sql(
        public val details: PgenErrorDetails,
        msg: String,
        throwable: Throwable? = null,
    ) : PgenException(msg, throwable)

    public class IntegrityConstraintViolation(details: PgenErrorDetails, msg: String, t: Throwable? = null) :
        Sql(details, msg, t)

    public class RestrictViolation(details: PgenErrorDetails, msg: String, t: Throwable? = null) : Sql(details, msg, t)
    public class NotNullViolation(details: PgenErrorDetails, msg: String, t: Throwable? = null) : Sql(details, msg, t)
    public class ForeignKeyViolation(details: PgenErrorDetails, msg: String, t: Throwable? = null) :
        Sql(details, msg, t)

    public class UniqueViolation(details: PgenErrorDetails, msg: String, t: Throwable? = null) : Sql(details, msg, t)
    public class CheckViolation(details: PgenErrorDetails, msg: String, t: Throwable? = null) : Sql(details, msg, t)
    public class ExclusionViolation(details: PgenErrorDetails, msg: String, t: Throwable? = null) : Sql(details, msg, t)
    public class Generic(details: PgenErrorDetails, msg: String, t: Throwable? = null) : Sql(details, msg, t)
    public class Other(msg: String, t: Throwable? = null) : PgenException(msg, t)
}