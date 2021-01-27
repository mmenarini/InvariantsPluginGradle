package edu.ucsd.daikonplugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileType
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.net.URI
import java.nio.file.Files
import javax.inject.Inject


open class Tests @Inject constructor(val workerExecutor: WorkerExecutor) : DefaultTask() {
    companion object {
        const val OUTPUT_FILE_NAME = "testEntryPoints.txt"
    }
    @get:Incremental
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    val classFiles = project.objects.fileCollection()

    @Internal
    lateinit var additionalClassPath: FileCollection

    @OutputDirectory
    val outputDirectory: DirectoryProperty = project.objects.directoryProperty()


    @TaskAction
    internal fun tests(inputChanges: InputChanges) {
        Files.createDirectories(outputDirectory.get().asFile.toPath())
        project.tasks.withType(Test::class.java).forEach { test ->
            System.err.println("Test Classpath = ${test.classpath.asPath}")
            val realCP = test.classpath.filter {
                it.exists()
            }.asPath
            System.err.println("RealCP = $realCP")
            val classesFiles = test.classpath.filter {
                it.exists() && it.isDirectory
            }.asPath
            System.err.println("classesFiles = $classesFiles")
            val tmpRepos = project.repositories.toList()
            project.repositories.clear()
            project.repositories.add(project.repositories.maven{ it.url=
                    URI("https://soot-build.cs.uni-paderborn.de/nexus/repository/swt-upb/") })
            project.repositories.add(project.repositories.jcenter())

            var sootConfig = project.configurations.findByName("sootconfig")
            if (sootConfig==null) {
                sootConfig = project.configurations.create("sootconfig")
                project.dependencies.add("sootconfig","ca.mcgill.sable:soot:3.2.0")
            }

            val addedClasses = ArrayList<String>()
            val changedClasses = ArrayList<String>()
            val removedClasses = ArrayList<String>()
            inputChanges.getFileChanges(classFiles).forEach { change ->
                if (change.fileType == FileType.DIRECTORY) return@forEach

                if (change.changeType == ChangeType.REMOVED) {
                    removedClasses.add(change.normalizedPath)
                } else if (change.changeType == ChangeType.MODIFIED) {
                    changedClasses.add(change.normalizedPath)
                } else {
                    addedClasses.add(change.normalizedPath)
                }
            }

            workerExecutor.submit(TestsWorkerSoot::class.java) {
                it.isolationMode = IsolationMode.CLASSLOADER //
                // .CLASSLOADER // .PROCESS
                // Constructor parameters for the unit of work implementation
                it.params(
                        addedClasses,
                        changedClasses,
                        removedClasses,
                        realCP,
                        outputDirectory.get().asFile.absolutePath)
                it.classpath(sootConfig)
                //it.forkOptions.maxHeapSize = "4G"
            }
            workerExecutor.await()
            project.repositories.clear()
            project.repositories.addAll(tmpRepos)
        }
    }

}
