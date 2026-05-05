package de.quati.pgen.plugin

import de.quati.pgen.plugin.intern.tasks.flywayMigration
import de.quati.pgen.plugin.intern.tasks.generateCode
import de.quati.pgen.plugin.intern.tasks.generateSpec
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension


@Suppress("unused")
public class PgenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val configBuilder = project.extensions.create("pgen", ConfigBuilder::class.java)
        val configProvider = project.provider { configBuilder.build() }
        val mainName = when (project.kotlinExtension) {
            is KotlinMultiplatformExtension -> "commonMain"
            is KotlinProjectExtension -> "main"
        }
        val genDir = project.layout.buildDirectory.dir("generated/sources/pgen/src/$mainName/kotlin")

        val pgenGenerateSpec = project.tasks.register("pgenGenerateSpec") { task ->
            task.group = TASK_GROUP
            task.outputs.upToDateWhen { false }
            task.doLast {
                val config = configProvider.get()
                flywayMigration(config, logger = it.logger)
                generateSpec(config = config)
            }
        }

        val pgenGenerateCode = project.tasks.register("pgenGenerateCode") { task ->
            task.mustRunAfter(pgenGenerateSpec)
            task.group = TASK_GROUP
            task.inputs.files(configProvider.map { it.specFilePath })
            task.outputs.dir(genDir)
            task.doLast {
                generateCode(config = configProvider.get(), outputPath = genDir.get(), logger = it.logger)
            }
        }

        project.afterEvaluate {
            project.kotlinExtension.sourceSets.findByName(mainName)?.kotlin?.srcDir(pgenGenerateCode)
        }
    }

    private companion object {
        private const val TASK_GROUP = "quati tools"
    }
}
