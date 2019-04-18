package edu.ucsd.daikonplugin


import com.github.javaparser.JavaParser
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.net.URI
import java.nio.file.Files
import javax.inject.Inject


open class Tests @Inject constructor(val workerExecutor: WorkerExecutor) : DefaultTask() {
    companion object {
        const val OUTPUT_FILE_NAME = "testEntryPoints.txt"
    }
    @InputFiles
    val classFiles = project.objects.property(FileCollection::class.java)

    @Internal
    lateinit var additionalClassPath: FileCollection
//    @Internal
//    lateinit var daikonTaskProvider: TaskProvider<Daikon>

    @OutputDirectory
    val outputDirectory: DirectoryProperty = project.objects.directoryProperty()

    @TaskAction
    internal fun tests() {
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
            //project.dependencies.add("sootconfig","org.jetbrains.kotlin:kotlin-reflect:1.3.20")
            //sootConfig.resolve()

            workerExecutor.submit(TestsWorkerSoot::class.java) {
                it.isolationMode = IsolationMode.PROCESS
                // Constructor parameters for the unit of work implementation
                it.params(
                        classesFiles,
                        realCP,
                        outputDirectory.get().asFile.absolutePath)
                it.classpath(sootConfig)
                it.forkOptions.maxHeapSize = "4G"
            }
            workerExecutor.await()
            project.repositories.clear()
            project.repositories.addAll(tmpRepos)
        }
    }

}
