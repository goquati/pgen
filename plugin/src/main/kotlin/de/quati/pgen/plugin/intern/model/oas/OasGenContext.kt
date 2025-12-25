package de.quati.pgen.plugin.intern.model.oas

internal data class OasGenContext(
    val pathPrefix: String,
    val meta: MetaOasData,
    val oasCommonName: String,
)
