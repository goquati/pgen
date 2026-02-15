package de.quati.pgen.plugin.intern.codegen.oas

import de.quati.pgen.plugin.intern.model.oas.OasGenContext
import de.quati.pgen.plugin.intern.model.oas.TableFieldOasData
import de.quati.pgen.plugin.intern.model.oas.TableFieldTypeOasData
import de.quati.pgen.plugin.intern.model.oas.TableOasData
import kotlin.collections.forEach

private fun YamlBuilder.addOpenApiRequired(data: List<TableFieldOasData>) = when (data.isEmpty()) {
    true -> "required: []".let(::add)
    false -> indent("required:") {
        data.map { "- " + it.name }.let(::add)
    }
}

context(c: OasGenContext)
private fun YamlBuilder.add(
    data: TableFieldTypeOasData,
    nullable: Boolean,
): Unit = when (data) {
    is TableFieldTypeOasData.Type -> {
        "type: ${data.type}".let(::add)
        data.format?.let { "format: $it" }?.let(::add)
        "nullable: true".takeIf { nullable }?.let(::add) ?: Unit
    }

    is TableFieldTypeOasData.Enum -> when (nullable) {
        false -> "$REF: '${data.getRef()}'".let(::add)
        true -> {
            indent("allOf:") {
                "- $REF: '${data.getRef()}'".let(::add)
            }
            "nullable: true".let(::add)
        }
    }

    is TableFieldTypeOasData.Array -> {
        "type: array".let(::add)
        "nullable: true".takeIf { nullable }?.let(::add)
        indent("items:") {
            add(data.items, nullable = false)
        }
    }
}


context(c: OasGenContext)
private fun YamlBuilder.addOpenApiProperties(data: List<TableFieldOasData>) = when (data.isEmpty()) {
    true -> "properties: {}".let(::add)
    false -> indent("properties:") {
        data.forEach { field ->
            indent("${field.name}:") {
                add(field.type, nullable = field.nullable)
            }
        }
    }
}

context(c: OasGenContext)
internal fun YamlBuilder.addReadDto(data: TableOasData) {
    "${data.nameCapitalized}:".let(::add)
    indent {
        "type: object".let(::add)
        addOpenApiRequired(data.fields)
        addOpenApiProperties(data.fields)
    }
}

context(c: OasGenContext)
internal fun YamlBuilder.addCreateDto(data: TableOasData) {
    "${data.nameCapitalized}Create:".let(::add)
    indent {
        "type: object".let(::add)
        addOpenApiRequired(data.fieldsAtCreate)
        addOpenApiProperties(data.fieldsAtCreate)
    }
}

context(c: OasGenContext)
internal fun YamlBuilder.addUpdateDto(data: TableOasData) {
    "${data.nameCapitalized}Update:".let(::add)
    indent {
        "type: object".let(::add)
        addOpenApiProperties(data.fieldsAtUpdate)
    }
}
