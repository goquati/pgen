package de.quati.pgen.plugin.intern.service

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readLines

class EnvFileService(private val path: Path) {
    constructor(path: String) : this(Path(path))

    private val allEnvs = path.readLines()
        .filter { it.isNotBlank() }
        .filter { !it.startsWith("#") }
        .associate { it.substringBefore('=') to it.substringAfter('=') }

    operator fun get(name: String) = allEnvs[name]?.takeIf { it.isNotBlank() }
        ?: throw Exception("'$name' not set in '$path'")
}