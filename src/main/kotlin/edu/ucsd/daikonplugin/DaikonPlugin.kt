package edu.ucsd.daikonplugin

import org.gradle.api.*
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.process.internal.worker.child.WorkerProcessClassPathProvider
import java.io.File
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
            if (project.hasProperty("daikonJarFile")) {
                val f = Paths.get(project.property("daikonJarFile").toString());
                if (Files.exists(f) && !extension.daikonJarFileName.isPresent){
                    extension.daikonJarFileName.set(f.toAbsolutePath().toString())
                }
            }

            val testTasks = project.tasks.withType(Test::class.java)

            // Register Invariants
            val invariantsTask = project.tasks.register("invariants", Invariants::class.java)

            // Register Daikon
            val daikonTask = project.tasks.register("daikon", Daikon::class.java)

            // We register one task for each test SourceSet
            val jpc = project.convention.getPlugin(JavaPluginConvention::class.java)
            val testSourceSets = jpc.sourceSets.asMap.filterKeys { it.startsWith("test") }.values
            val testsDetectTasks = mutableListOf<TaskProvider<TestsDetect>>()
            val callgraphTasks = mutableListOf<TaskProvider<Callgraph>>()
            testSourceSets.forEach { sourceSet ->
                // Register & Configure TestDetect
                val testsDetectTask = project.tasks.register(
                        "testsDetect_"+sourceSet.name, TestsDetect::class.java) { task ->

                    val classFiles = sourceSet.output.asFileTree.matching{ t -> t.include("**/*.class")}.files
                    task.classFiles.from(classFiles)
                    task.compileClassPath.from(sourceSet.compileClasspath)
                    task.sourceSetId.set(sourceSet.name)
                    task.outputDirectory.set(extension.callgraphOutputDirectory.getOrElse(TestsDetect.STD_FOLDER(project)))
                }
                testsDetectTasks.add(testsDetectTask)

                // Register & Configure Callgraph

                val callgraphTask = project.tasks.register(
                        "callgraph_"+sourceSet.name, Callgraph::class.java) { task ->

                    val classFiles = jpc.sourceSets.fold(mutableSetOf<File>()){
                        filesSet, sourceSet ->
                        filesSet.addAll(sourceSet.output.asFileTree.matching{ t -> t.include("**/*.class")}.files)
                        return@fold filesSet
                    }
                    task.classFiles.from(classFiles)
                    task.testEntryPoints.set(testsDetectTask.get().outputFile)
                    task.classPath.from(sourceSet.runtimeClasspath)
                    task.sourceSetId.set(sourceSet.name)
                    task.outputDirectory.set(extension.callgraphOutputDirectory.getOrElse(Callgraph.STD_FOLDER(project)))
                }
                callgraphTasks.add(callgraphTask)
            }


            val daikonAfterTestTask = project.tasks.register("daikonAfterTest", DaikonAfterTest::class.java) {
                it.mustRunAfter(testTasks)
                it.dependsOn(daikonTask)
                val stdDir = project.layout.projectDirectory.dir("${project.buildDir}/daikon")
                it.inputFile.set(extension.daikonOutputDirectory.getOrElse(stdDir).file("test.inv.gz"))
                it.outputFile.set(extension.daikonOutputDirectory.getOrElse(stdDir).file("result.inv.gz"))
            }
            // Register & Configure cleanTestsDetect
            project.tasks.create("cleanTestsDetect") {
                it.actions.add(Action<Task> { testsDetectTasks.forEach { it.get().outputs.upToDateWhen { false } } })
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
                it.actions.add(Action<Task> { callgraphTasks.forEach { it.get().outputs.upToDateWhen { false } } })
            }
            // Configure Invariants
            invariantsTask.configure { task ->
                task.dependsOn(daikonAfterTestTask)
                task.inputFile.set(daikonAfterTestTask.get().outputFile)
                task.daikonJarPath = extension.getDaikonJarPath().toAbsolutePath().toString()
                if (project.hasProperty("daikonPattern"))
                    task.daikonPattern.set(project.property("daikonPattern").toString())
                else if (extension.pattern.isPresent)
                    task.daikonPattern.set(extension.pattern)
                else
                    task.daikonDirectory.set(daikonTask.get().outputDirectory)
                val stdFolder = project.layout.projectDirectory.dir("${project.buildDir}/invariants")
                task.outputDirectory.set(extension.invariantsOutputDirectory.getOrElse(stdFolder))
            }
            // Configure Daikon
            daikonTask.configure { task ->
                //Making sure that invariants are found even if some taks fails
                testTasks.forEach{it.ignoreFailures=true }

                //task.dependsOn(callgraphTask)
                project.gradle.startParameter.isContinueOnFailure = true
                testTasks.forEach{it.outputs.upToDateWhen{false}}
                task.finalizedBy(testTasks)
                task.afterDaikonTask = daikonAfterTestTask.get()

                val stdDir = project.layout.projectDirectory.dir("${project.buildDir}/daikon")
                task.outputDirectory.set(extension.daikonOutputDirectory.getOrElse(stdDir))

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
