package edu.ucsd.daikonplugin

import edu.ucsd.callgraphplugin.CallGraphWorker
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.nio.file.Files
import javax.inject.Inject

open class Invariants @Inject constructor(val workerExecutor: WorkerExecutor): DefaultTask() {

    @Internal
    lateinit var daikonJarPath: String
    @Input
    val daikonPattern: Property<String> = project.objects.property(String::class.java)

    @InputFile
    val inputFile = project.objects.fileProperty()

    @OutputDirectory
    //@Optional
    var outputDirectory : DirectoryProperty = project.objects.directoryProperty()

    @TaskAction
    internal fun doStuff() {
        val stdFolder = project.layout.projectDirectory.dir("${project.buildDir}/invariants")
        val outputDirectoryPath = outputDirectory.getOrElse(stdFolder).asFile.toPath().toAbsolutePath()
        Files.createDirectories(outputDirectoryPath)

        if (!daikonPattern.isPresent)
            throw Exception("Cannot run daikon without a daikonPattern specified")

        workerExecutor.submit(InvariantsWorker::class.java) {
            // Use the minimum level of isolation
            it.isolationMode = IsolationMode.CLASSLOADER

            // Constructor parameters for the unit of work implementation
            it.params(daikonPattern.get(),
                    inputFile.get().asFile.absolutePath.toString(),
                    outputDirectoryPath.toString())

            it.classpath(project.layout.files(daikonJarPath))
        }


    }

}