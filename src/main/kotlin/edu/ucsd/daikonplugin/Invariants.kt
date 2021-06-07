package edu.ucsd.daikonplugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.nio.file.Files
import javax.inject.Inject

open class Invariants @Inject constructor(private val workerExecutor: WorkerExecutor): DefaultTask() {

    @Internal
    lateinit var daikonJarPath: String

    @Input
    @Optional
    val daikonPattern = project.objects.property(String::class.java)

    @InputDirectory
    @Optional
    val daikonDirectory = project.objects.directoryProperty()

    @InputFile
    val inputFile = project.objects.fileProperty()

    @OutputDirectory
    val outputDirectory : DirectoryProperty = project.objects.directoryProperty()

    @TaskAction
    internal fun invariants() {
        var  selectedPattern = when {
            daikonPattern.isPresent -> daikonPattern.get()
            daikonDirectory.isPresent -> daikonDirectory.asFile.get().resolve("path.txt").readText()
            else -> throw Exception("Cannot run daikon without a daikonPattern or a daikon directory specified")
        }

        val outputDirectoryPath = outputDirectory.get().asFile.toPath().toAbsolutePath()
        Files.createDirectories(outputDirectoryPath)

        workerExecutor.submit(InvariantsWorker::class.java) {
            // Use the minimum level of isolation
            it.isolationMode = IsolationMode.CLASSLOADER

            // Constructor parameters for the unit of work implementation
            it.params(selectedPattern,
                    inputFile.get().asFile.absolutePath.toString(),
                    outputDirectoryPath.toString())

            it.classpath(project.layout.files(daikonJarPath))
        }
        workerExecutor.await()
    }

}