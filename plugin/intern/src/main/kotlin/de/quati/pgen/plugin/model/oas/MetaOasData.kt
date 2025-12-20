package de.quati.pgen.plugin.model.oas

data class MetaOasData(
    val oasVersion: String = "3.0.3",
    val title: String,
    val version: String,
)