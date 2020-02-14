package edu.ucsd.daikonplugin


import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
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
import java.nio.file.Path
import javax.inject.Inject


open class Callgraph @Inject constructor(@Internal val workerExecutor: WorkerExecutor) : DefaultTask() {
    companion object {
        fun STD_FOLDER(project: Project) : Directory {
            return project.layout.projectDirectory.dir("${project.buildDir}/callgraph")
        }
    }
    @get:Incremental
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    val classFiles = project.objects.fileCollection()

    @get:Incremental
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFile
    val testEntryPoints = project.objects.fileProperty()

    @get:Incremental
    @get:Classpath
    @get:InputFiles
    val classPath = project.objects.fileCollection()

    @get:Input
    val sourceSetId = project.objects.property(String::class.java)

//    @Internal
//    lateinit var additionalClassPath: FileCollection
//    @Internal
//    lateinit var daikonTaskProvider: TaskProvider<Daikon>

    @get:OutputDirectory
    //@Optional
    val outputDirectory: DirectoryProperty = project.objects.directoryProperty()

    private val utils = TestsResultsUtils()
    private fun toTestsFilename(relativePath: String): Path {
        val classToOutput = outputDirectory.asFile.get().toPath().resolve(relativePath)
        Files.createDirectories(classToOutput.parent)
        val filename = classToOutput.fileName.toString().removeSuffix(".class")+"."+sourceSetId+".tests"
        return classToOutput.parent.resolve(filename)
    }

    @TaskAction
    internal fun callgraph(inputChanges: InputChanges) {
        val testsToAnalyze = mutableSetOf<String>()
        val testMethodsList = mutableSetOf<String>()
        val testClassesSet = mutableSetOf<String>()
        Files.createDirectories(outputDirectory.get().asFile.toPath())
        val cachedTestEntryPointFile = outputDirectory.get().asFile.resolve(testEntryPoints.get().asFile.name)
        if (inputChanges.isIncremental) {
            if (cachedTestEntryPointFile.exists())
                utils.LoadTestMethodsList(testClassesSet, testMethodsList, cachedTestEntryPointFile)
            var testMethodsListNew = mutableSetOf<String>()
            var testClassesSetNew = mutableSetOf<String>()
            utils.LoadTestMethodsList(testClassesSetNew, testMethodsListNew, testEntryPoints.get().asFile)
            testsToAnalyze.addAll(testMethodsListNew.minus(testMethodsList))
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
            removedClasses.forEach {
                val testsFile = toTestsFilename(it)
                if (Files.exists(testsFile)){
                    testsToAnalyze.addAll(testsFile.toFile().readLines())
                    Files.delete(testsFile)
                }
            }
            changedClasses.forEach {
                val testsFile = toTestsFilename(it)
                if (Files.exists(testsFile)){
                    testsToAnalyze.addAll(testsFile.toFile().readLines())
                    Files.delete(testsFile)
                }
            }
        } else {
            utils.LoadTestMethodsList(testClassesSet, testMethodsList, testEntryPoints.get().asFile)
            testsToAnalyze.addAll(testMethodsList)
        }
        runSootGC(testClassesSet, testsToAnalyze)
//        daikonTaskProvider.get().outputs.upToDateWhen { false }
        Files.copy(testEntryPoints.get().asFile.toPath(), cachedTestEntryPointFile.toPath())
/*
        if(sourceFile.isPresent && lineNumber.isPresent) {
            val signature = computeMethodSignature(sourceFile.get().asFile, lineNumber.get())
            runSootGC(signature)
            daikonTaskProvider.get().outputs.upToDateWhen { false }
        } else if (methodSignature.isPresent) {
            runSootGC(methodSignature.get())
            daikonTaskProvider.get().outputs.upToDateWhen { false }
        } else
          logger.warn("No method or file/line provided did not run the Callgraph analysis")
*/
    }

/*   private fun computeMethodSignature(fileName: File, line: Int) : String {
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
    }*/
    private fun runSootGC(testsClasses: Set<String>, tests: Set<String>) {
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


        val realClasspath = classPath.filter{it.exists()}.asPath
        val classes = classFiles.filter{it.exists() && !it.isDirectory()}.files.map { it.absolutePath }
        logger.info("RealCP = $realClasspath")
        logger.info("classesFiles = $classes")


        workerExecutor.processIsolation(){
            it.classpath.setFrom(sootConfig)
            it.forkOptions.maxHeapSize = "4G"
            //NOTE: Enable the following onyl when debugging the TestDetectWorkerSoot class
            //it.forkOptions.debug = true
        }.submit(CallGraphWorkerSoot::class.java) {
            it.testsClasses.addAll(testsClasses)
            it.tests.addAll(tests)
            it.applicationClasses.addAll(classes)
            it.classPath=realClasspath
            it.sourceSetId=sourceSetId.get()
            it.outputDir=outputDirectory.asFile.get().toPath()
        }

        workerExecutor.await()
        project.repositories.clear()
        project.repositories.addAll(tmpRepos)
    }

//    private fun runSootGC(methodName: String) {
//        project.tasks.withType(Test::class.java).forEach { test ->
//            System.err.println("Test Classpath = ${test.classpath.asPath}")
//            val realCP = test.classpath.filter {
//                it.exists()
//            }.asPath
//            System.err.println("RealCP = $realCP")
//            val classesFiles = test.classpath.filter {
//                it.exists() && it.isDirectory
//            }.asPath
//            System.err.println("classesFiles = $classesFiles")
//            val tmpRepos = project.repositories.toList()
//            project.repositories.clear()
//            project.repositories.add(project.repositories.maven{ it.url=
//                    URI("https://soot-build.cs.uni-paderborn.de/nexus/repository/swt-upb/") })
//            project.repositories.add(project.repositories.jcenter())
//
//            var sootConfig = project.configurations.findByName("sootconfig")
//            if (sootConfig==null) {
//                sootConfig = project.configurations.create("sootconfig")
//                project.dependencies.add("sootconfig","ca.mcgill.sable:soot:3.2.0")
//            }
//            //project.dependencies.add("sootconfig","org.jetbrains.kotlin:kotlin-reflect:1.3.20")
//            //sootConfig.resolve()
//
//            workerExecutor.submit(CallGraphWorkerSoot::class.java) {
//                it.isolationMode = IsolationMode.CLASSLOADER
//                //PROCESS
//                // Constructor parameters for the unit of work implementation
//                it.params(
//                        classesFiles,
//                        methodName,
//                        realCP,
//                        testEntryPoints.get().asFile.absolutePath,
//                        outputDirectory.get().asFile.absolutePath)
//                it.classpath(sootConfig)
//                //it.forkOptions.maxHeapSize = "4G"
//            }
//            workerExecutor.await()
//            project.repositories.clear()
//            project.repositories.addAll(tmpRepos)
//        }
//    }

}
