package edu.ucsd.daikonplugin

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import java.io.File
import java.nio.file.Files


open class Daikon : DefaultTask() {

    @Internal
    lateinit var additionalClassPath: FileCollection
    @Internal
    lateinit var daikonJarPath: String
    @Internal
    lateinit var afterDaikonTask: Task

    @Input
    @Optional
    val daikonPattern = project.objects.property(String::class.java)

    @InputDirectory
    val callGraphDirectory = project.objects.directoryProperty()

    @InputFile
    @Optional
    val sourceFile = project.objects.fileProperty()
    @Input
    @Optional
    val lineNumber = project.objects.property(Int::class.java)
    @Input
    @Optional
    val methodSignature = project.objects.property(String::class.java)
    @OutputDirectory
    var outputDirectory = project.objects.directoryProperty()

    //    @OutputFile
//    var outputFile = project.objects.fileProperty()
    private val utils = TestsResultsUtils()

    private fun signatureToTestName(signature: String):String {
        return signature.substringAfter("<").substringBefore(":") +
                "."+signature.substringBefore("(").substringAfterLast(" ")
    }

    @TaskAction
    internal fun daikon() {

        var signature = ""
        if(sourceFile.isPresent && lineNumber.isPresent) {
            signature = computeMethodSignature(sourceFile.get().asFile, lineNumber.get())
        } else if (methodSignature.isPresent) {
            signature = methodSignature.get()
        } else
            logger.warn("No method or file/line provided did not run the Callgraph analysis")
        if (signature=="") return

        val outputDirectoryPath = outputDirectory.get().asFile.toPath()
        val outputFilePath = outputDirectoryPath.resolve("test.inv.gz")
        try {
            Files.deleteIfExists(outputFilePath)
            Files.deleteIfExists(outputDirectoryPath.resolve("result.inv.gz"))
            logger.warn("Removed test.inv.gz and result.inv.gz")
        } catch(e: Throwable) {
            logger.error("Failed to remove the old daikon results file", e)
        }


        val selectedClass=signature.substringAfter("<").substringBefore(":")
        val selectedMethod = signature.substringBefore("(").substringAfterLast(" ")

        val filename = utils.filterMethodNametoFilename(
                selectedClass.replace('.',File.separatorChar)+
                        File.separatorChar+selectedMethod+
                        //"("+method.parameterTypes.map { it.toString() }.joinToString(",")+")"+
                        ".tests")

        val tests=callGraphDirectory.asFile.get().resolve(filename).readLines()
        if (tests.isEmpty()) {
            System.out.println("Not tests found.")
            return
        }
        afterDaikonTask.outputs.upToDateWhen { false }


        val selectedPattern= "^${selectedClass.replace(".", "\\.")}\\.$selectedMethod"
        outputDirectory.get().asFile.resolve("path.txt").printWriter().use { out ->
            System.out.println("Writing path.txt, will contain $selectedPattern")
            out.println(selectedPattern)
        }


        project.tasks.withType(Test::class.java).configureEach { test ->
            test.outputs.upToDateWhen { false }
            test.maxParallelForks = 1
            test.setForkEvery(0)
            test.ignoreFailures = true

            test.setTestNameIncludePatterns(tests.map { signatureToTestName(it) })

            Files.createDirectories(outputDirectoryPath)

            if (System.getProperty("os.name").toLowerCase().startsWith("win")) {
                val newScript = outputDirectoryPath.resolve("gettyJava.exe")
                if(!Files.exists(newScript)){
                    javaClass.getResourceAsStream("/gettyJava.exe").buffered(32000).use {
                        input ->
                        newScript.toFile().outputStream().use {
                            output ->
                            input.copyTo(output)
                        }
                    }
                }
                val newScriptConfig = outputDirectoryPath.resolve("gettyJava.config")
                javaClass.getResourceAsStream("/gettyJava.config").bufferedReader().use { input ->
                    if (Files.exists(newScriptConfig)) Files.delete(newScriptConfig)
                    newScriptConfig.toFile().printWriter().use { output ->
                        input.lines().forEach { line ->
                            if (line.contains("@OUTPUT_FILE@"))
                                output.println(line.replace("@OUTPUT_FILE@", outputFilePath.toString()))
                            else if (line.contains("@DAIKON_JAR@"))
                                output.println(line.replace("@DAIKON_JAR@", daikonJarPath))
                            else if (line.contains("@PATTERN@"))
                                output.println(line.replace("@PATTERN@", selectedPattern))
                            else
                                output.println(line)
                        }
                    }
                }
                test.executable = newScript.toString()
            } else {
                val newScript = outputDirectoryPath.resolve("java-daikon.sh")
                //Create a new script
                javaClass.getResourceAsStream("/java-daikon.sh").bufferedReader().use { input ->
                    if (Files.exists(newScript)) Files.delete(newScript)
                    newScript.toFile().printWriter().use { output ->
                        input.lines().forEach { line ->
                            if (line.contains("@OUTPUT_FILE@"))
                                output.println(line.replace("@OUTPUT_FILE@", outputFilePath.toString()))
                            else if (line.contains("@DAIKON_JAR@"))
                                output.println(line.replace("@DAIKON_JAR@", daikonJarPath))
                            else if (line.contains("@PATTERN@"))
                                output.println(line.replace("@PATTERN@", selectedPattern))
                            else
                                output.println(line)
                        }
                    }
                    if (!Files.isExecutable(newScript))
                        newScript.toFile().setExecutable(true, true)
                }
                test.executable = newScript.toString()
            }
            test.classpath = test.classpath.plus(additionalClassPath)
        }
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
}
