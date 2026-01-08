package buildkit

import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.register

fun Project.registerStartDbTask(
    name: String = "startDb",
    profile: String,
) {
    tasks.register<Exec>(name) {
        outputs.upToDateWhen { false }
        runCmd("docker compose --profile $profile up -d --force-recreate --wait")
    }
}

private fun Exec.runCmd(cmd: String) {
    if (OperatingSystem.current().isWindows) {
        commandLine("cmd", "/c", cmd)
    } else {
        commandLine("bash", "-lc", cmd)
    }
}