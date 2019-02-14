package edu.ucsd.callgraphplugin


import com.github.javaparser.JavaParser
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.model.typesystem.ReferenceTypeImpl
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
import java.net.URI
import java.nio.file.Files
import javax.inject.Inject


open class Callgraph @Inject constructor(val workerExecutor: WorkerExecutor) : DefaultTask() {

    @InputFile
    val sourceFile = project.objects.fileProperty()
    @Input
    val lineNumber = project.objects.property(Int::class.java)

    @Internal
    lateinit var additionalClassPath: FileCollection

    @OutputDirectory
    //@Optional
    val outputDirectory: DirectoryProperty = project.objects.directoryProperty()

    @TaskAction
    internal fun graph() {
        val sourceFolders =  project.convention.getPlugin(JavaPluginConvention::class.java).getSourceSets()

        Files.createDirectories(outputDirectory.get().asFile.toPath())

        logger.info("Creating Type Resolvers")
        val reflectionTypeSolver = ReflectionTypeSolver()
        reflectionTypeSolver.parent = reflectionTypeSolver
        val combinedSolver = CombinedTypeSolver()
        combinedSolver.add(reflectionTypeSolver)

        sourceFolders.forEach { sourceFolder ->
            sourceFolder.allJava.sourceDirectories
                .filter{it.exists()}
                .forEach{combinedSolver.add(JavaParserTypeSolver(it))}
            sourceFolder.compileClasspath.asFileTree
                .filter { it.isFile && it.name.endsWith(".jar")}
                .forEach{combinedSolver.add(JarTypeSolver(it))}
        }

        val symbolSolver = JavaSymbolSolver(combinedSolver)
        JavaParser.getStaticConfiguration().setSymbolResolver(symbolSolver)

        val cu = JavaParser.parse(sourceFile.get().asFile)


        val methods  = cu.findAll(MethodDeclaration::class.java) {
            (lineNumber.get() >= it.begin.get().line)
                    && (lineNumber.get() <= it.end.get().line) }

        if (!methods.isEmpty()) {
            if (methods.size>1)
                logger.warn("Multiple methods returned for line ${lineNumber.get()} in file ${sourceFile.get()}")
            val method = methods.first()

            logger.info("Run test subset analysis for ${method.resolve().qualifiedSignature}")
            val methodName="<${method.resolve().declaringType().qualifiedName}: " +
                           "${method.type.resolve().describe()} " +
                           "${method.name}" +
                           "(${method.resolve().qualifiedSignature
                                   .substringAfter("(")
                                   .split(", ").joinToString(",")}>"
            runSootGC(methodName)

        } else {
            logger.info("No method found in file ${sourceFile.get()} for line ${lineNumber.get()}")
        }

    }

    private fun runSootGC(methodName: String) {
        project.tasks.withType(Test::class.java).forEach { test ->
            val realCP = test.classpath.filter {
                it.exists()
            }.asPath
            val classesFiles = test.classpath.filter {
                it.exists() && it.isDirectory
            }.asPath
            val tmpRepos = project.repositories.toList()
            project.repositories.clear()
            project.repositories.add(project.repositories.maven{ it.url=
                    URI("https://soot-build.cs.uni-paderborn.de/nexus/repository/swt-upb/") })
            project.repositories.add(project.repositories.jcenter())

            val sootConfig = project.configurations.create("sootconfig")
            project.dependencies.add("sootconfig","ca.mcgill.sable:soot:3.2.0")
            //project.dependencies.add("sootconfig","org.jetbrains.kotlin:kotlin-reflect:1.3.20")
            //sootConfig.resolve()

            workerExecutor.submit(CallGraphWorkerSoot::class.java) {
                it.isolationMode = IsolationMode.PROCESS
                // Constructor parameters for the unit of work implementation
                it.params(
                        classesFiles,
                        methodName,
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
