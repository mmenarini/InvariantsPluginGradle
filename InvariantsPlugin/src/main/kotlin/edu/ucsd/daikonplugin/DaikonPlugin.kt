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
import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Inject


open class DaikonPlugin @Inject constructor(
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

            val callgraphTask = project.tasks.register("callgraph", Callgraph::class.java) { task ->
                if (project.hasProperty("invariantsSourceFile")) {
                    val f = Paths.get(
                            project.layout.files(
                                    project.property("invariantsSourceFile").toString()).asPath)
                    if (!Files.isRegularFile(f))
                        throw Exception("Could not find java file $f")
                    task.sourceFile.set(f.toFile())
                } else {
                    task.sourceFile.set(extension.sourceFile)
                }

                if (project.hasProperty("invariantsSourceFileLineNumber")) {
                    val lineN  = Integer.parseInt(
                                    project.property("invariantsSourceFileLineNumber").toString())
//                    if (lineN==null)
//                        throw Exception("Property invariantsSourceFileLineNumber must e an integer but " +
//                                "${project.property("invariantsSourceFileLineNumber").toString()} " +
//                                "was specified")
                    task.lineNumber.set(lineN)
                } else {
                    task.lineNumber.set(extension.sourceFileLineNumber)
                }

                task.additionalClassPath = getAdditionalClassPathFiles(project, extension)

                val stdFolder = project.layout.projectDirectory.dir("${project.buildDir}/callgraph")
                task.outputDirectory.set(extension.callgraphOutputDirectory.getOrElse(stdFolder))
            }
            val daikonTask = project.tasks.register("daikon", Daikon::class.java) { task ->
                task.finalizedBy(testTasks)

                val stdDir = project.layout.projectDirectory.dir("${project.buildDir}/daikon")
                task.outputFile.set(extension.daikonOutputDirectory.getOrElse(stdDir).file("test.inv.gz"))

                if (project.hasProperty("daikonPattern"))
                    task.daikonPattern.set(project.property("daikonPattern").toString())
                else
                    task.daikonPattern.set(extension.pattern)
                task.additionalClassPath = getAdditionalClassPathFiles(project, extension)
                task.daikonJarPath = extension.getDaikonJarPath().toAbsolutePath().toString()
            }
            project.tasks.register("invariants", Invariants::class.java) { task ->
                task.mustRunAfter(daikonTask)
                task.mustRunAfter(testTasks)
                task.inputFile.set(daikonTask.get().outputFile)
                task.daikonJarPath = extension.getDaikonJarPath().toAbsolutePath().toString()
                if (project.hasProperty("daikonPattern"))
                    task.daikonPattern.set(project.property("daikonPattern").toString())
                else
                    task.daikonPattern.set(extension.pattern)
                task.outputDirectory.set(extension.invariantsOutputDirectory)
            }
        }
    }
}
