package de.quati.pgen.plugin.util.codegen.oas

import de.quati.pgen.plugin.model.oas.TableOasData

private val TableOasData.idSchema
    get() = listOfNotNull(
        "type: string",
        idFormat?.let { "format: $idFormat" },
    )


internal fun YamlBuilder.addReadEndpoint(data: TableOasData) {
    indent("get:") {
        "summary: Get a ${data.namePretty} by ID".let(::add)
        "operationId: get${data.nameCapitalized}".let(::add)
        indent("tags:") {
            "- ${data.tag}".let(::add)
        }
        indent("parameters:") {
            indent("- in: path") {
                "name: id".let(::add)
                "required: true".let(::add)
                indent("schema:") {
                    data.idSchema.let(::add)
                }
            }
        }
        indent("responses:") {
            indent("'200':") {
                "description: A ${data.namePretty}".let(::add)
                indent("content:") {
                    indent("application/json:") {
                        indent("schema:") {
                            "$REF: '#/components/schemas/${data.nameCapitalized}'".let(::add)
                        }
                    }
                }
            }
        }
    }
}

internal fun YamlBuilder.addUpdateEndpoint(data: TableOasData) {
    indent("put:") {
        "summary: Update a ${data.namePretty} by ID".let(::add)
        "operationId: update${data.nameCapitalized}".let(::add)
        indent("tags:") {
            "- ${data.tag}".let(::add)
        }
        indent("parameters:") {
            indent("- in: path") {
                "name: id".let(::add)
                "required: true".let(::add)
                indent("schema:") {
                    data.idSchema.let(::add)
                }
            }
        }
        indent("requestBody:") {
            "required: true".let(::add)
            indent("content:") {
                indent("application/json:") {
                    indent("schema:") {
                        "$REF: '#/components/schemas/${data.nameCapitalized}Update'".let(::add)
                    }
                }
            }
        }
        indent("responses:") {
            indent("'200':") {
                "description: ${data.namePretty} updated".let(::add)
                indent("content:") {
                    indent("application/json:") {
                        indent("schema:") {
                            "$REF: '#/components/schemas/${data.nameCapitalized}'".let(::add)
                        }
                    }
                }
            }
        }
    }
}


internal fun YamlBuilder.addDeleteEndpoint(data: TableOasData) {
    indent("delete:") {
        "summary: Delete a ${data.namePretty} by ID".let(::add)
        "operationId: delete${data.nameCapitalized}".let(::add)
        indent("tags:") {
            "- ${data.tag}".let(::add)
        }
        indent("parameters:") {
            indent("- in: path") {
                "name: id".let(::add)
                "required: true".let(::add)
                indent("schema:") {
                    data.idSchema.let(::add)
                }
            }
        }
        indent("responses:") {
            indent("'204':") {
                "description: ${data.namePretty} deleted".let(::add)
            }
        }
    }
}

internal fun YamlBuilder.addReadAllEndpoint(data: TableOasData) {
    indent("get:") {
        "summary: Get all ${data.namePretty}".let(::add)
        "operationId: getAll${data.nameCapitalized}".let(::add)
        indent("tags:") {
            "- ${data.tag}".let(::add)
        }
        indent("responses:") {
            indent("'200':") {
                "description: A list of all ${data.namePretty}".let(::add)
                indent("content:") {
                    indent("application/json:") {
                        indent("schema:") {
                            "type: array".let(::add)
                            indent("items:") {
                                "$REF: '#/components/schemas/${data.nameCapitalized}'".let(::add)
                            }
                        }
                    }
                    indent("application/x-ndjson:") {
                        indent("schema:") {
                            "type: array".let(::add)
                            indent("items:") {
                                "$REF: '#/components/schemas/${data.nameCapitalized}'".let(::add)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun YamlBuilder.addCreateEndpoint(data: TableOasData) {
    indent("post:") {
        "summary: Create a new ${data.namePretty}".let(::add)
        "operationId: create${data.nameCapitalized}".let(::add)
        indent("tags:") {
            "- ${data.tag}".let(::add)
        }
        indent("requestBody:") {
            "required: true".let(::add)
            indent("content:") {
                indent("application/json:") {
                    indent("schema:") {
                        "$REF: '#/components/schemas/${data.nameCapitalized}Create'".let(::add)
                    }
                }
            }
        }
        indent("responses:") {
            indent("'201':") {
                "description: ${data.namePretty} created".let(::add)
                indent("content:") {
                    indent("application/json:") {
                        indent("schema:") {
                            "$REF: '#/components/schemas/${data.nameCapitalized}'".let(::add)
                        }
                    }
                }
            }
        }
    }
}
