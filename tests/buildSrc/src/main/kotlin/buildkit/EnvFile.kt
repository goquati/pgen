package buildkit

import org.gradle.api.Project
import kotlin.io.path.readText

class EnvFile(project: Project, name: String = "config") {
    private val variables = project.projectDir.toPath().resolve(name).readText().lines().mapNotNull { line ->
        val (k, v) = line.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
        k to v
    }.toMap()

    fun getDbPort(name: String): Int {
        val key = "DB_PORT_${name.uppercase()}".replace('-', '_')
        if (key !in variables) error("$key not found")
        return variables[key]!!.toInt()
    }
}
