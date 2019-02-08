package edu.ucsd.callgraphplugin


import com.github.javaparser.JavaParser
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
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
import org.gradle.workers.WorkerExecutor
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

        val testJavaFiles = sourceFolders.getByName(SourceSet.TEST_SOURCE_SET_NAME)
                .allJava.files

//        val classPathJars = javaPluginConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME)
//                .compileClasspath.asFileTree
//                .filter { it.isFile && it.name.endsWith(".jar")}

        logger.info("Creating Type Resolvers")
        val reflectionTypeSolver = ReflectionTypeSolver()
        reflectionTypeSolver.setParent(reflectionTypeSolver)
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
            logger.info("Run test subset analysis for ${method.nameAsString}")


            Files.createDirectories(outputDirectory.get().asFile.toPath())

            val CallersSet = HashSet<MethodDeclaration>()
            val ExplorationSet = HashSet<MethodDeclaration>()
            ExplorationSet.add(method)

            while (ExplorationSet.isNotEmpty()){
                val curMethod = ExplorationSet.first()
                ExplorationSet.remove(curMethod)
                CallersSet.add(curMethod)
                curMethod.findAll(MethodCallExpr::class.java).forEach{
                    val containingMethod = it.findAncestor(MethodDeclaration::class.java)
                    if (containingMethod.isPresent && !CallersSet.contains(containingMethod.get()))
                        ExplorationSet.add(containingMethod.get())
                }
            }
            logger.info("Calling Set Found:")
            outputDirectory.get().asFile.toPath().resolve("methodsSet.txt").toFile().printWriter().use {
                writer->
                CallersSet.forEach {
                    logger.info(it.nameAsString)
                    writer.println(it.nameAsString)
                }
            }

//            workerExecutor.submit(CallGraphWorker::class.java) {
//                // Use the minimum level of isolation
//                it.isolationMode = IsolationMode.NONE
//
//                // Constructor parameters for the unit of work implementation
//                it.params(outputDirectoryPath.toString()) //(file, project.file("$outputDir/${file.name}"))
//            }
        } else {
            logger.info("No method found in file ${sourceFile.get()} for line ${lineNumber.get()}")
        }

    }
}
