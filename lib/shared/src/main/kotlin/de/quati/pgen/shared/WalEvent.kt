package de.quati.pgen.shared

import java.time.OffsetDateTime

public sealed interface WalEvent<T : Any> {
    public val metaData: MetaData

    public data class MetaData(
        public val timestamp: OffsetDateTime,
    )

    public data class Message(
        override val metaData: MetaData,
        val transactional: Boolean,
        val prefix: String,
        val content: String,
    ) : WalEvent<Nothing>

    public interface Change<T : Any> : WalEvent<T> {
        public val table: TableNameWithSchema
        public val payload: Payload<T>

        public data class Base<T : Any>(
            override val table: TableNameWithSchema,
            override val metaData: MetaData,
            override val payload: Payload<T>,
        ) : Change<T>

        public sealed interface Payload<out T : Any> {

            public fun <R : Any> map(mapper: (T) -> R): Payload<R> = when (this) {
                is Delete -> Delete(
                    dataOld = mapper(dataOld),
                )

                is Insert -> Insert(
                    dataNew = mapper(dataNew),
                )

                is Update -> Update(
                    dataOld = mapper(dataOld),
                    dataNew = mapper(dataNew),
                )

                is Truncate -> this
            }

            public data class Delete<T : Any>(
                val dataOld: T,
            ) : Payload<T>

            public data class Insert<T : Any>(
                val dataNew: T,
            ) : Payload<T>

            public data class Update<T : Any>(
                val dataOld: T,
                val dataNew: T,
            ) : Payload<T>

            public data object Truncate : Payload<Nothing>
        }
    }
}
