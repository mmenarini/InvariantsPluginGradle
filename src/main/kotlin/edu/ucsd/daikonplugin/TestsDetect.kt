package edu.ucsd.daikonplugin

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.tasks.*
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.net.URI
import java.nio.file.Files
import javax.inject.Inject

@CacheableTask
open class TestsDetect @Inject constructor(@Internal val workerExecutor: WorkerExecutor) : DefaultTask() {
    companion object {
        const val OUTPUT_FILE_NAME = "testEntryPoints.txt"
        fun STD_FOLDER(project:Project) : Directory {
            return project.layout.projectDirectory.dir("${project.buildDir}/testsDetect")
        }
    }

    @get:Incremental
    @get:CompileClasspath
    @get:InputFiles
    val compileClassPath = project.objects.fileCollection()

    @get:Incremental
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    val classFiles = project.objects.fileCollection()

    @get:Input
    val sourceSetId = project.objects.property(String::class.java)

    @get:OutputDirectory
    val outputDirectory: DirectoryProperty = project.objects.directoryProperty()

    @get:Internal
    val outputFile : File
        get() = outputDirectory.asFile.get().toPath().resolve("""${sourceSetId.get()}.${OUTPUT_FILE_NAME}""").toFile()

    @TaskAction
    internal fun tests(inputChanges: InputChanges) {

        Files.createDirectories(outputDirectory.get().asFile.toPath())

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

        val realClasspath = compileClassPath.filter{it.exists()}.asPath
        val classes = classFiles.filter{it.exists() && !it.isDirectory()}.asPath
        logger.info("RealCP = $realClasspath")
        logger.info("classesFiles = $classes")


        val _addedClasses = mutableListOf<String>()
        val _changedClasses =  mutableListOf<String>()
        val _removedClasses = mutableListOf<String>()

        inputChanges.getFileChanges(classFiles).forEach { change ->
            if (change.fileType == FileType.DIRECTORY) return@forEach

            if (change.changeType == ChangeType.REMOVED) {
                _removedClasses.add(change.normalizedPath)
            } else if (change.changeType == ChangeType.MODIFIED) {
                _changedClasses.add(change.normalizedPath)
            } else {
                _addedClasses.add(change.normalizedPath)
            }
        }

        workerExecutor.processIsolation(){
            it.classpath.setFrom(sootConfig)
            it.forkOptions.maxHeapSize = "4G"
            //NOTE: Enable the following onyl when debugging the TestDetectWorkerSoot class
            //it.forkOptions.debug = true
        }.submit(TestsDetectWorkerSoot::class.java) {
            it.addedClasses.addAll(_addedClasses)
            it.changedClasses.addAll(_changedClasses)
            it.removedClasses.addAll(_removedClasses)
            it.classPath=realClasspath
            it.outputDir=outputDirectory.asFile.get().toPath()
            it.outputFile=outputFile
        }

        workerExecutor.await()
        project.repositories.clear()
        project.repositories.addAll(tmpRepos)

    }
}
