package de.quati.pgen.core

import de.quati.pgen.shared.TableNameWithSchema
import de.quati.pgen.shared.WalEvent
import kotlinx.serialization.json.JsonObject

public interface PgenTable {
    public fun getTableNameWithSchema(): TableNameWithSchema
}
