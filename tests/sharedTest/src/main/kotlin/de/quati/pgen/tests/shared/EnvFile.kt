package de.quati.pgen.tests.shared

import kotlin.io.path.Path
import kotlin.io.path.readText

object EnvFile {
    private val envPath = Path(".")
        .toAbsolutePath()
        .normalize()
        .let { path ->
            generateSequence(path) { it.parent }
                .first { it.fileName?.toString() == "tests" }
                .resolve("config")
        }
    private val variables = envPath.readText().lines().mapNotNull { line ->
        val (k, v) = line.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
        k to v
    }.toMap()

    fun getDbPort(name: String): Int {
        val key = "DB_PORT_${name.uppercase()}".replace('-', '_')
        if (key !in variables) error("$key not found")
        return variables[key]!!.toInt()
    }
}
