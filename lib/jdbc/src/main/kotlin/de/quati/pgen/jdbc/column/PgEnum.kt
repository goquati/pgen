package de.quati.pgen.jdbc.column

import de.quati.pgen.core.column.PgEnum
import org.postgresql.util.PGobject

public fun PgEnum.toDbObject(): PGobject = PGobject().apply {
    value = pgEnumLabel
    type = pgEnumTypeName
}
