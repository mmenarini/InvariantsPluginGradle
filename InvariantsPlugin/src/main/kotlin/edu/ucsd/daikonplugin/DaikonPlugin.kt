package edu.ucsd.daikonplugin

import edu.ucsd.callgraphplugin.Callgraph
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.testing.Test
import org.gradle.process.internal.worker.child.WorkerProcessClassPathProvider
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject


open class DaikonPlugin @Inject constructor(val workerExecutor: WorkerExecutor,
                                            val workerProcessClassPathProvider:WorkerProcessClassPathProvider)  : Plugin<Project> {

    private fun getAdditionalClassPathFiles(project: Project, extension:DaikonExtension): FileCollection {
        val workerMainClassPath = workerProcessClassPathProvider.findClassPath("WORKER_MAIN")?.asFiles
        return project.layout.files(extension.getDaikonJarPath(), workerMainClassPath)
    }
    override fun apply(project: Project) {
        project.plugins.withType(JavaPlugin::class.java) {

            val extension = project.extensions.create("daikonConfig",
                    DaikonExtension::class.java, project.objects)

            val testTasks = project.tasks.withType(Test::class.java)

            project.tasks.register("daikon", Daikon::class.java) { task ->
                task.finalizedBy(testTasks)
                task.outputDirectory.set(extension.daikonOutputDirectory)
                if (project.hasProperty("daikonPattern"))
                    task.daikonPattern.set(project.property("daikonPattern").toString())
                else
                    task.daikonPattern.set(extension.pattern)
                task.additionalClassPath = getAdditionalClassPathFiles(project, extension)
                task.daikonJarPath = extension.getDaikonJarPath().toAbsolutePath().toString()
            }

            project.tasks.register("callgraph", Callgraph::class.java) { task ->
                task.message.set("Hello")
                task.recipient.set("Massi")
                task.additionalClassPath = getAdditionalClassPathFiles(project, extension)
                task.outputDirectory.set(extension.callgraphOutputDirectory)
            }
        }
    }
}
