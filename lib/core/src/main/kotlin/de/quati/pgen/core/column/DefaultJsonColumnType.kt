package de.quati.pgen.core.column

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import org.jetbrains.exposed.v1.json.JsonColumnType

public class DefaultJsonColumnType(json: Json = Json) : JsonColumnType<JsonElement>(
    { json.encodeToString(serializer<JsonElement>(), it) },
    { json.decodeFromString(serializer<JsonElement>(), it) }
)
