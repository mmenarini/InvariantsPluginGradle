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
import org.gradle.work.InputChanges
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.net.URI
import java.nio.file.Files
import javax.inject.Inject


open class CallgraphSingle @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {
    @InputFiles
    val classFiles = project.objects.property(FileCollection::class.java)
    @InputFile
    val testEntryPoints = project.objects.fileProperty()

    @InputFile
    @Optional
    val sourceFile = project.objects.fileProperty()
    @Input
    @Optional
    val lineNumber = project.objects.property(Int::class.java)
    @Input
    @Optional
    val methodSignature = project.objects.property(String::class.java)

    @Internal
    lateinit var additionalClassPath: FileCollection
    @Internal
    lateinit var daikonTaskProvider: TaskProvider<Daikon>

    @OutputDirectory
    //@Optional
    val outputDirectory: DirectoryProperty = project.objects.directoryProperty()

    @TaskAction
    internal fun callgraph(inputChanges: InputChanges) {
        Files.createDirectories(outputDirectory.get().asFile.toPath())

        if(sourceFile.isPresent && lineNumber.isPresent) {
            val signature = computeMethodSignature(sourceFile.get().asFile, lineNumber.get())
            runSootGC(signature)
            daikonTaskProvider.get().outputs.upToDateWhen { false }
        } else if (methodSignature.isPresent) {
            runSootGC(methodSignature.get())
            daikonTaskProvider.get().outputs.upToDateWhen { false }
        } else
          logger.warn("No method or file/line provided did not run the Callgraph analysis")
    }

    private fun computeMethodSignature(fileName: File, line: Int) : String {
        logger.info("Creating Type Resolvers")
        val reflectionTypeSolver = ReflectionTypeSolver()
        reflectionTypeSolver.parent = reflectionTypeSolver
        val combinedSolver = CombinedTypeSolver()
        combinedSolver.add(reflectionTypeSolver)

        val sourceFolders =  project.convention.getPlugin(JavaPluginConvention::class.java).getSourceSets()
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
            return methodName
        } else {
            logger.info("No method found in file ${sourceFile.get()} for line ${lineNumber.get()}")
            return ""
        }
    }
    private fun runSootGC(methodName: String) {
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
                project.dependencies.add("sootconfig","ca.mcgill.sable:soot:4.1.0")
            }
            //project.dependencies.add("sootconfig","org.jetbrains.kotlin:kotlin-reflect:1.3.20")
            //sootConfig.resolve()

            workerExecutor.submit(CallGraphWorkerSoot::class.java) {
                it.isolationMode = IsolationMode.CLASSLOADER
                //PROCESS
                // Constructor parameters for the unit of work implementation
                it.params(
                        classesFiles,
                        methodName,
                        realCP,
                        testEntryPoints.get().asFile.absolutePath,
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
