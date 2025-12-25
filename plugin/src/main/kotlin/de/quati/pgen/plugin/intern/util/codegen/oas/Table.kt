package de.quati.pgen.plugin.intern.util.codegen.oas

import de.quati.pgen.plugin.CRUD
import de.quati.pgen.plugin.intern.model.oas.OasGenContext
import de.quati.pgen.plugin.intern.model.oas.TableOasData
import org.intellij.lang.annotations.Language

@Language("yaml")
context(c: OasGenContext)
internal fun TableOasData.toOpenApi() = yaml(level = 0) {
    "openapi: ${c.meta.oasVersion}".let(::add)
    indent("info:") {
        "title: ${c.meta.title}".let(::add)
        "version: ${c.meta.version}".let(::add)
    }

    if (endpoints.isNotEmpty())
        indent("paths:") {
            if (CRUD.READ_ALL in endpoints || CRUD.CREATE in endpoints)
                indent("${c.pathPrefix}/$path:") {
                    if (CRUD.READ_ALL in endpoints) addReadAllEndpoint(this@toOpenApi)
                    if (CRUD.CREATE in endpoints) addCreateEndpoint(this@toOpenApi)
                }

            if (CRUD.READ in endpoints || CRUD.UPDATE in endpoints ||
                CRUD.DELETE in endpoints
            )
                indent("${c.pathPrefix}/$path/{id}:") {
                    if (CRUD.READ in endpoints) addReadEndpoint(this@toOpenApi)
                    if (CRUD.UPDATE in endpoints) addUpdateEndpoint(this@toOpenApi)
                    if (CRUD.DELETE in endpoints) addDeleteEndpoint(this@toOpenApi)
                }

        }
    val dtos = endpoints.flatMap { it.requiredDtos }.toSet().sorted()
    if (dtos.isNotEmpty())
        indent("components:") {
            indent("schemas:") {
                if (DtoType.GET in dtos) addReadDto(this@toOpenApi)
                if (DtoType.CREATE in dtos) addCreateDto(this@toOpenApi)
                if (DtoType.UPDATE in dtos) addUpdateDto(this@toOpenApi)
            }
        }
}


