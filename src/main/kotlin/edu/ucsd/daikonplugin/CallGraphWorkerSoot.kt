package edu.ucsd.daikonplugin

import soot.*
import soot.options.Options
import soot.tagkit.VisibilityAnnotationTag
import java.io.File
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.inject.Inject


open class CallGraphWorkerSoot @Inject constructor(
        val applicationClasses: String,
        val classPath: String,
        val testsClasses: Set<String>,
        val tests: Set<String>,
        val outputDirectory: String) : Runnable {
    private val utils = TestsResultsUtils()
    override fun run() {
        soot.G.reset()
        Files.createDirectories(Paths.get(outputDirectory))
        //runLibraryMode()
        runTestEntryPointsMode()
    }

    private fun runTestEntryPointsMode() {
        soot.G.reset()
        val args = mutableListOf(//"-pp",
                "-w", "-allow-phantom-refs", "-ice",
                "-p", "cg", "all-reachable:false,verbose:true,library:disabled",
                "-p", "bb", "enabled:false",
                "-p", "tag", "enabled:false",
                "-f", "n",
                "-cp", classPath,
                "-d", outputDirectory//,
                //methodName.substringAfter('<').substringBefore(':')
        )
        Options.v().parse(args.toTypedArray())
        val entryPointsList = ArrayList<SootMethod>()
        testsClasses.forEach {
            var c = Scene.v().forceResolve(it,SootClass.BODIES)
            c.setApplicationClass()
        }
        Scene.v().loadNecessaryClasses()
        tests.forEach {
            var method = Scene.v().getMethod(it)
            entryPointsList.add(method)
        }
        val setOfClassFiles = mutableSetOf<String>()
        applicationClasses.split(":").forEach {
            val path = Paths.get(it)
            for (f in path.toFile().walkTopDown()) {
                if(f.isFile && f.name.endsWith(".class")) {
                    setOfClassFiles.add(utils.classFilenameToClassname(f.toPath().subpath(path.nameCount,f.toPath().nameCount).toString()))
                }
            }
        }
        Scene.v().entryPoints = entryPointsList
        val wjtp = PackManager.v().getPack("wjtp")
        wjtp.add(
                Transform("wjtp.AllTestFinder", object : SceneTransformer() {
                    override fun internalTransform(phaseName: String?, options: MutableMap<String, String>?) {
                        processAllCallGraph(setOfClassFiles,entryPointsList)
                    }
                }))

        PackManager.v().runPacks()
    }
    private fun processAllCallGraph(classes: Set<String>, testMethods: ArrayList<SootMethod>) {
        System.out.println("Finding classes for tests in list")

        //val classesSoot = classes.map { Scene.v().forceResolve(it,SootClass.BODIES) }
        //val testMap = HashMap<String, MutableSet<SootMethod>>()
        val methodsMap = HashMap<String, MutableSet<String>>()
        testMethods.forEach { testMethod ->
            val methodsToProcess = mutableSetOf<SootMethod>()
            val methodsProcessed = mutableSetOf<SootMethod>()
            methodsToProcess.add(testMethod)
            while (methodsToProcess.isNotEmpty()) {
                methodsProcessed.addAll(methodsToProcess)
                methodsToProcess.toList().forEach {
                    Scene.v().callGraph.edgesOutOf(it).forEach {
                        if (classes.contains(it.tgt.method().declaringClass.name)) {
                            methodsToProcess.add(it.tgt.method())
                        }
                    }
                }
                methodsToProcess.removeAll(methodsProcessed)
            }
            methodsProcessed.forEach{
                if(!methodsMap.containsKey(it.signature))
                    methodsMap[it.signature] = mutableSetOf()
                methodsMap[it.signature]?.add(testMethod.signature)
            }
            //testMap.put(testMethod.signature, methodsProcessed)
        }
        methodsMap.entries.forEach{ (methodName, testsSet) ->
            val method = Scene.v().getMethod(methodName)
            val filename = utils.filterMethodNametoFilename(
                    method.declaringClass.name.replace(".", File.separator)+
                    File.separator+method.name+
                    //"("+method.parameterTypes.map { it.toString() }.joinToString(",")+")"+
                    ".tests")
            try {
                val filePath = Paths.get(outputDirectory).resolve(Paths.get(filename))

                Files.createDirectories(filePath.parent)
                val composedTests = mutableSetOf<String>()
                if (filePath.toFile().exists()) {
                    composedTests.addAll(filePath.toFile().readLines())
                }
                composedTests.addAll(testsSet)
                filePath.toFile().printWriter().use { printer -> composedTests.forEach { printer.println(it) } }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
        /*Paths.get(outputDirectory).resolve("testSet.txt").toFile().printWriter().use { out ->
            System.out.println("Writing testSet.txt, will contain ${testMethods.count()} tests")
            testMethods.forEach {
                out.println("${it.method().declaringClass.name}.${it.method().name}")
            }
        }*/

    }
    private fun processClassCallGraph(targetClass:SootClass) {
        System.out.println("Finding tests for class ${targetClass.name}")
        val methodsToProcess = HashSet<MethodOrMethodContext>()
        val methodsProcessed = HashSet<MethodOrMethodContext>()
        val testMethods = HashSet<MethodOrMethodContext>()
        methodsToProcess.addAll(targetClass.methods)
        while (methodsToProcess.isNotEmpty()) {
            methodsProcessed.addAll(methodsToProcess)
            methodsToProcess.toList().forEach {
                Scene.v().callGraph.edgesInto(it).forEach {
                    methodsToProcess.add(it.src)
                    if (isTest(it.src.method())) {
                        testMethods.add(it.src)
                    }
                }
            }
            methodsToProcess.removeAll(methodsProcessed)
        }
        if (testMethods.isEmpty())
            throw Exception("Did not find any test for class${targetClass.name}")
        Paths.get(outputDirectory).resolve("path.txt").toFile().printWriter().use { out ->
            val pattern = "^${targetClass.name.replace(".", "\\.")}\\.*"
            System.out.println("Writing path.txt, will contain $pattern")
            out.println(pattern)
        }
        Paths.get(outputDirectory).resolve("testSet.txt").toFile().printWriter().use { out ->
            System.out.println("Writing testSet.txt, will contain ${testMethods.count()} tests")
            testMethods.forEach {
                out.println("${it.method().declaringClass.name}.${it.method().name}")
            }
        }
    }
    private fun processMethodCallGraph(targetMethod:SootMethod) {
        System.out.println("Finding tests for method ${targetMethod.name}")
        val methodsToProcess = HashSet<MethodOrMethodContext>()
        val methodsProcessed = HashSet<MethodOrMethodContext>()
        val testMethods = HashSet<MethodOrMethodContext>()
        methodsToProcess.add(targetMethod)
        while (methodsToProcess.isNotEmpty()) {
            methodsProcessed.addAll(methodsToProcess)
            methodsToProcess.toList().forEach {
                Scene.v().callGraph.edgesInto(it).forEach {
                    methodsToProcess.add(it.src)
                    if (isTest(it.src.method())) {
                        testMethods.add(it.src)
                    }
                }
            }
            methodsToProcess.removeAll(methodsProcessed)
        }
        Paths.get(outputDirectory).resolve("path.txt").toFile().printWriter().use { out ->
            val pattern =
                    "^${targetMethod.declaringClass.name.replace(".", "\\.")}\\.${targetMethod.name}"
            System.out.println("Writing path.txt, will contain $pattern")
            out.println(pattern)
        }
        if (testMethods.isEmpty())
            throw Exception("Did not find any test for method ${targetMethod.name}")
        Paths.get(outputDirectory).resolve("testSet.txt").toFile().printWriter().use { out ->
            System.out.println("Writing testSet.txt, will contain ${testMethods.count()} tests")
            testMethods.forEach {
                out.println("${it.method().declaringClass.name}.${it.method().name}")
            }
        }
    }
    private fun implementsTest(c: SootClass, s: String): Boolean {
        //if (c == null) return false
        if (c.implementsInterface(s))
            return true
        if (c.hasSuperclass())
            return implementsTest(c.superclass, s)
        return false
    }

    private val testAnnotationTag = "Lorg/junit/Test;"
    private fun isTest(m: SootMethod): Boolean {
        if (implementsTest(m.declaringClass, "junit.framework.Test"))
            return true
        return m.tags.any {
            if (it is VisibilityAnnotationTag)
                it.annotations.any { testAnnotationTag == it.type }
            else false
        }
    }
}