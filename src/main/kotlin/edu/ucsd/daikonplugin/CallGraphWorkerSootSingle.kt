package edu.ucsd.daikonplugin

import soot.*
import soot.options.Options
import soot.tagkit.VisibilityAnnotationTag
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.inject.Inject


open class CallGraphWorkerSootSingle @Inject constructor(
        val applicationClasses: String,
        val methodName: String,
        val classPath: String,
        val testEntryPoints: String,
        val outputDirectory: String) : Runnable {

    override fun run() {
        soot.G.reset()
        Files.createDirectories(Paths.get(outputDirectory))
        //runLibraryMode()
        runTestEntryPointsMode()
    }

    private fun runTestEntryPointsMode() {
        val args = mutableListOf(//"-pp",
                "-w", "-allow-phantom-refs", "-ice",
                "-p", "cg", "all-reachable:false,verbose:true,library:disabled",
                "-p", "bb", "enabled:false",
                "-p", "tag", "enabled:false",
                "-f", "n",
                "-cp", classPath,
                "-d", outputDirectory,
                methodName.substringAfter('<').substringBefore(':')
        )
        Options.v().parse(args.toTypedArray())
        var processingClasses = true
        val entryPointsList = ArrayList<SootMethod>()
        val entryPoints = Paths.get(testEntryPoints)
        if (!(Files.exists(entryPoints) && Files.isRegularFile(entryPoints)))
            throw Exception("File ${testEntryPoints} not found!")
        entryPoints.toFile().bufferedReader().use { input ->
            input.useLines { sequence ->
                sequence.forEach {
                    if (processingClasses) {
                        if (it.startsWith("C:")) {
                            //Still loading classes
                            var c = Scene.v().forceResolve(it.substring(2),SootClass.BODIES)
                            c.setApplicationClass()
                        } else if (it.startsWith("M:")) {
                            //Switching to Adding Methods
                            processingClasses=false
                            Scene.v().loadNecessaryClasses()
                            var method = Scene.v().getMethod(it.substring(2))
                            entryPointsList.add(method)
                        } else {
                            throw Exception("should not have strings not starting with C: or  M: here")
                        }
                    } else {
                        if (it.startsWith("M:")) {
                            //Adding Methods to list
                            var method = Scene.v().getMethod(it.substring(2))
                            entryPointsList.add(method)
                        } else {
                            throw Exception("should not have strings not starting with M: here")
                        }
                    }
                }
            }
        }
        Scene.v().entryPoints = entryPointsList
        val wjtp = PackManager.v().getPack("wjtp")
        wjtp.add(
                Transform("wjtp.AllTestFinder", object : SceneTransformer() {
                    override fun internalTransform(phaseName: String?, options: MutableMap<String, String>?) {
                        val targetMethod = Scene.v().grabMethod(methodName)
                        if (targetMethod == null) {
                            //Not in a method lets look for a class
                            val targetClass = Scene.v()
                                    .getSootClass(methodName.substringAfter('<')
                                            .substringBefore(':'))
                            if (targetClass == null)
                                throw Exception("Target method nor class were found. The signature used was $methodName.")
                            else
                                processClassCallGraph(targetClass)
                        } else
                            processMethodCallGraph(targetMethod)
                    }
                }))

        PackManager.v().runPacks()

    }

    private fun runLibraryMode() {
        val args = mutableListOf(//"-pp",
                "-w", "-allow-phantom-refs", "-ice",
                "-p", "cg", "all-reachable:true,verbose:true,library:any-subtype",
                "-p", "bb", "enabled:false",
                "-p", "tag", "enabled:false",
                "-f", "n",
                "-cp", classPath,
                "-d", outputDirectory,
                methodName.substringAfter('<').substringBefore(':')
        )

        applicationClasses.split(":")
                .forEach { args.addAll(arrayOf("-process-dir", it)) }
        try {
            val wjtp = PackManager.v().getPack("wjtp")
            System.err.println("Removing old TestFinder")
            wjtp.removeAll { true }
            //wjtp.remove("wjtp.TestFinder")
            System.err.println("Removing old TestFinder Done")
            wjtp.add(
                    Transform("wjtp.TestFinder", object : SceneTransformer() {
                        override fun internalTransform(phaseName: String?, options: MutableMap<String, String>?) {
                            val targetMethod = Scene.v().grabMethod(methodName)
                            if (targetMethod == null) {
                                //Not in a method lets look for a class
                                val targetClass = Scene.v()
                                        .getSootClass(methodName.substringAfter('<')
                                                .substringBefore(':'))
                                if (targetClass == null)
                                    throw Exception("Target method nor class were found. The signature used was $methodName.")
                                else
                                    processClassCallGraph(targetClass)
                            } else
                                processMethodCallGraph(targetMethod)
                        }
                    }))
        }catch (ex:Exception) {
            System.err.println(ex.message)
            ex.printStackTrace()
        }
        soot.Main.main(args.toTypedArray())
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