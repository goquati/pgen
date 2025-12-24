package de.quati.pgen.plugin.intern.model.oas

data class OasGenContext(
    val pathPrefix: String,
    val meta: MetaOasData,
    val oasCommonName: String,
)
