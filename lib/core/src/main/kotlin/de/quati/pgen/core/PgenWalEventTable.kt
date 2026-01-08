package de.quati.pgen.core

import de.quati.pgen.shared.WalEvent
import kotlinx.serialization.json.JsonObject

public interface PgenWalEventTable : PgenTable {
    public fun walEventMapper(event: WalEvent.Change<JsonObject>): WalEvent.Change<*>
}
