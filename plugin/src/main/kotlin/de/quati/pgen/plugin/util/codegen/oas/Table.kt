package de.quati.pgen.plugin.util.codegen.oas

import de.quati.pgen.plugin.model.config.Config
import de.quati.pgen.plugin.model.oas.OasGenContext
import de.quati.pgen.plugin.model.oas.TableOasData
import org.intellij.lang.annotations.Language

@Language("yaml")
context(c: OasGenContext)
fun TableOasData.toOpenApi() = yaml(level = 0) {
    "openapi: ${c.meta.oasVersion}".let(::add)
    indent("info:") {
        "title: ${c.meta.title}".let(::add)
        "version: ${c.meta.version}".let(::add)
    }

    if (endpoints.isNotEmpty())
        indent("paths:") {
            if (Config.OasConfig.CRUD.READ_ALL in endpoints || Config.OasConfig.CRUD.CREATE in endpoints)
                indent("${c.pathPrefix}/$path:") {
                    if (Config.OasConfig.CRUD.READ_ALL in endpoints) addReadAllEndpoint(this@toOpenApi)
                    if (Config.OasConfig.CRUD.CREATE in endpoints) addCreateEndpoint(this@toOpenApi)
                }

            if (Config.OasConfig.CRUD.READ in endpoints || Config.OasConfig.CRUD.UPDATE in endpoints ||
                Config.OasConfig.CRUD.DELETE in endpoints
            )
                indent("${c.pathPrefix}/$path/{id}:") {
                    if (Config.OasConfig.CRUD.READ in endpoints) addReadEndpoint(this@toOpenApi)
                    if (Config.OasConfig.CRUD.UPDATE in endpoints) addUpdateEndpoint(this@toOpenApi)
                    if (Config.OasConfig.CRUD.DELETE in endpoints) addDeleteEndpoint(this@toOpenApi)
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


