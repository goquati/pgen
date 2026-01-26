package de.quati.pgen.plugin

import de.quati.pgen.plugin.intern.tasks.flywayMigration
import de.quati.pgen.plugin.intern.tasks.generateCode
import de.quati.pgen.plugin.intern.tasks.generateOas
import de.quati.pgen.plugin.intern.tasks.generateSpec
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension


@Suppress("unused")
public class PgenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val configBuilder = project.extensions.create("pgen", ConfigBuilder::class.java)
        val mainName = when (project.kotlinExtension) {
            is KotlinMultiplatformExtension -> "commonMain"
            is KotlinProjectExtension -> "main"
        }
        val genDir = project.layout.buildDirectory.dir("generated/sources/pgen/src/$mainName/kotlin")

        val pgenGenerateSpec = project.tasks.register("pgenGenerateSpec") { task ->
            task.group = TASK_GROUP
            task.outputs.upToDateWhen { false }
            task.doLast {
                val config = configBuilder.build()
                generateSpec(config = config)
            }
        }

        val pgenGenerateCode = project.tasks.register("pgenGenerateCode") { task ->
            task.mustRunAfter(pgenGenerateSpec)
            task.group = TASK_GROUP
            task.outputs.dir(genDir)
            task.outputs.upToDateWhen { false }
            task.doLast {
                val out = project.serviceOf<StyledTextOutputFactory>().create("pgen")!!
                val config = configBuilder.build()
                generateCode(config = config, outputPath = genDir.get(), out = out)
            }
        }

        project.tasks.register("pgenGenerateOas") { task ->
            task.group = TASK_GROUP
            task.doLast {
                val out = project.serviceOf<StyledTextOutputFactory>().create("pgen")!!
                val config = configBuilder.build()
                generateOas(config = config, out = out)
            }
        }

        project.tasks.register("pgenGenerate") { task ->
            task.group = TASK_GROUP
            task.outputs.upToDateWhen { false }
            task.dependsOn(pgenGenerateSpec, pgenGenerateCode)
        }

        project.tasks.register("pgenFlywayMigration") { task ->
            task.group = TASK_GROUP
            task.doLast { task ->
                val out = project.serviceOf<StyledTextOutputFactory>().create("pgen")!!
                val config = configBuilder.build()
                flywayMigration(config, out)
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
