package de.quati.pgen.build

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class StartDbTestcontainerTask : DefaultTask() {
    @get:Input
    abstract val configs: ListProperty<DbTestcontainerConfig>

    init {
        group = "quati tools dev"
    }

    @TaskAction
    fun start() {
        configs.get().forEach { config ->
            DbContainers.start(config)
        }
    }
}
