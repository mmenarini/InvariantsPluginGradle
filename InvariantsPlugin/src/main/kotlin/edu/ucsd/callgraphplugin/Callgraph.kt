package edu.ucsd.callgraphplugin

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

open class Callgraph @Inject constructor(val workerExecutor: WorkerExecutor) : DefaultTask() {

    @Input
    val message: Property<String> = project.objects.property(String::class.java)
    @Input
    val recipient: Property<String> = project.objects.property(String::class.java)

    @Internal
    lateinit var additionalClassPath: FileCollection

    @OutputDirectory
    @Optional
    val outputDirectory: DirectoryProperty = project.objects.directoryProperty()

    @TaskAction
    internal fun graph() {

        val stdFolder = project.layout.projectDirectory.dir("${project.buildDir}/callgraph")
        val outputDirectoryPath = outputDirectory.getOrElse(stdFolder).asFile.toPath().toAbsolutePath()
        Files.createDirectories(outputDirectoryPath)

        workerExecutor.submit(CallGraphWorker::class.java) {
            // Use the minimum level of isolation
            it.isolationMode = IsolationMode.NONE

            // Constructor parameters for the unit of work implementation
            it.params(outputDirectoryPath.toString()) //(file, project.file("$outputDir/${file.name}"))
        }
        //System.out.printf("%s, %s!\n", message, recipient)

    }
}
