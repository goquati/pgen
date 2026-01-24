package de.quati.pgen.plugin.intern.tasks

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import de.quati.pgen.plugin.intern.model.config.Config
import de.quati.pgen.plugin.intern.model.sql.PgenSpec
import kotlinx.serialization.decodeFromString
import kotlin.io.path.notExists
import kotlin.io.path.readText

internal val yaml = Yaml(
    serializersModule = Yaml.default.serializersModule,
    configuration = YamlConfiguration(
        encodeDefaults = false,
        polymorphismStyle = PolymorphismStyle.Property,
    )
)

internal fun Config.loadSpec(): PgenSpec {
    if (specFilePath.notExists())
        error("Pgen spec file does not exist: '$specFilePath'")
    val spec = yaml.decodeFromString<PgenSpec>(specFilePath.readText())
    return spec
}