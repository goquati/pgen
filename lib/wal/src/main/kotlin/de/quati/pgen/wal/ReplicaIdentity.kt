package de.quati.pgen.wal

import de.quati.pgen.shared.TableNameWithSchema
import de.quati.pgen.shared.requireValidPgIdentifier
import org.intellij.lang.annotations.Language

public sealed interface ReplicaIdentity {
    public data object Default : ReplicaIdentity
    public data object Full : ReplicaIdentity
    public data object Nothing : ReplicaIdentity
    public data class UsingIndex(
        val indexName: String
    ) : ReplicaIdentity {
        init {
            requireValidPgIdentifier(indexName, what = "index")
        }
    }

    public companion object {
        @Language("PostgreSQL")
        internal fun ReplicaIdentity.toSql(table: TableNameWithSchema): String = when (this) {
            Default -> "alter table $table replica identity default;"
            Full -> "alter table $table replica identity full;"
            Nothing -> "alter table $table replica identity nothing;"
            is UsingIndex -> "alter table $table replica identity using index ($indexName);"
        }
    }
}