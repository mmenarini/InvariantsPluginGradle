package edu.ucsd.daikonplugin

import edu.ucsd.callgraphplugin.Callgraph
import org.apache.tools.ant.TaskAdapter
import org.gradle.api.*
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.process.internal.worker.child.WorkerProcessClassPathProvider
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

            // Register & Configure Callgraph
            val callgraphTask = project.tasks.register("callgraph", Callgraph::class.java) { task ->
                task.dependsOn("testClasses")
                if (project.hasProperty("methodSignature")) {
                    task.methodSignature.set(project.properties["methodSignature"].toString())
                } else if (extension.methodSignature.isPresent){
                    task.methodSignature.set(extension.methodSignature)
                } else {
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
                        val lineN = Integer.parseInt(
                                project.property("invariantsSourceFileLineNumber").toString())
                        task.lineNumber.set(lineN)
                    } else {
                        task.lineNumber.set(extension.sourceFileLineNumber)
                    }
                }

                task.additionalClassPath = getAdditionalClassPathFiles(project, extension)
                val stdFolder = project.layout.projectDirectory.dir("${project.buildDir}/callgraph")
                task.outputDirectory.set(extension.callgraphOutputDirectory.getOrElse(stdFolder))
            }
            // Register Invariants
            val invariantsTask = project.tasks.register("invariants", Invariants::class.java)
            // Register Daikon
            val daikonTask = project.tasks.register("daikon", Daikon::class.java)
            // Register & Configure cleanCallgraph
            val daikonAfterTestTask = project.tasks.register("daikonAfterTest", DaikonAfterTest::class.java) {
                it.mustRunAfter(testTasks)
                val stdDir = project.layout.projectDirectory.dir("${project.buildDir}/daikon")
                it.outputFile.set(extension.daikonOutputDirectory.getOrElse(stdDir).file("test.inv.gz"))
            }
            // Register & Configure cleanDaikon
            project.tasks.create("cleanDaikon") {
                it.actions.add(Action<Task> { daikonTask.get().outputs.upToDateWhen { false } })
            }
            // Register & Configure cleanInvariants
            project.tasks.create("cleanInvariants") {
                it.actions.add(Action<Task> { invariantsTask.get().outputs.upToDateWhen { false } })
            }
            // Register & Configure cleanCallgraph
            project.tasks.create("cleanCallgraph") {
                it.actions.add(Action<Task> { callgraphTask.get().outputs.upToDateWhen { false } })
            }
            // Configure Invariants
            invariantsTask.configure { task ->
                task.dependsOn(daikonAfterTestTask)
                task.inputFile.set(daikonTask.get().outputFile)
                task.daikonJarPath = extension.getDaikonJarPath().toAbsolutePath().toString()
                if (project.hasProperty("daikonPattern"))
                    task.daikonPattern.set(project.property("daikonPattern").toString())
                else if (extension.pattern.isPresent)
                    task.daikonPattern.set(extension.pattern)
                else
                    task.callGraphDirectory.set(callgraphTask.get().outputDirectory)
                val stdFolder = project.layout.projectDirectory.dir("${project.buildDir}/invariants")
                task.outputDirectory.set(extension.invariantsOutputDirectory.getOrElse(stdFolder))
            }
            // Configure Daikon
            daikonTask.configure { task ->
                task.finalizedBy(testTasks)
                task.afterDaikonTask = daikonAfterTestTask.get()

                val stdDir = project.layout.projectDirectory.dir("${project.buildDir}/daikon")
                task.outputFile.set(extension.daikonOutputDirectory.getOrElse(stdDir).file("test.inv.gz"))

                if (project.hasProperty("daikonPattern"))
                    task.daikonPattern.set(project.property("daikonPattern").toString())
                else if (extension.pattern.isPresent)
                    task.daikonPattern.set(extension.pattern)
                else
                    task.callGraphDirectory.set(callgraphTask.get().outputDirectory)
                task.additionalClassPath = getAdditionalClassPathFiles(project, extension)
                task.daikonJarPath = extension.getDaikonJarPath().toAbsolutePath().toString()
            }
        }
    }
}
