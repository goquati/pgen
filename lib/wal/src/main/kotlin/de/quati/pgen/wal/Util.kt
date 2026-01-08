package de.quati.pgen.wal

public fun pgenWalListener(
    slot: String,
    url: String,
    block: PgenWalEventListener.Builder.() -> Unit,
): PgenWalEventListener {
    return PgenWalEventListener.Builder(
        slot = slot,
        url = url,
    ).apply(block).build()
}
