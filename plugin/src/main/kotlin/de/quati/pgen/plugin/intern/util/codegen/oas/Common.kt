package de.quati.pgen.plugin.intern.util.codegen.oas

import de.quati.pgen.plugin.intern.model.oas.CommonOasData
import de.quati.pgen.plugin.intern.model.oas.EnumOasData
import de.quati.pgen.plugin.intern.model.oas.OasGenContext
import org.intellij.lang.annotations.Language

@Language("yaml")
context(c: OasGenContext)
fun CommonOasData.toOpenApi() = yaml(level = 0) {
    "openapi: ${c.meta.oasVersion}".let(::add)
    indent("info:") {
        "title: ${c.meta.title}".let(::add)
        "version: ${c.meta.version}".let(::add)
    }
    "paths: {}".let(::add)
    if (enums.isNotEmpty())
        indent("components:") {
            indent("schemas:") {
                enums.forEach {
                    add(it)
                }
            }
        }
}

@Language("yaml")
private fun YamlBuilder.add(data: EnumOasData) = indent(data.nameCapitalized + ":") {
    "type: string".let(::add)
    indent("enum:") {
        data.items.map { "- $it" }.let(::add)
    }
}