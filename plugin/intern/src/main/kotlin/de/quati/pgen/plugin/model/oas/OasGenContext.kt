package de.quati.pgen.plugin.model.oas

data class OasGenContext(
    val pathPrefix: String,
    val meta: MetaOasData,
    val oasCommonName: String,
)
