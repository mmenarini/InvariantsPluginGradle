package edu.ucsd.callgraphplugin

import soot.*
import soot.tagkit.AnnotationTag
import soot.tagkit.VisibilityAnnotationTag
import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Inject


open class CallGraphWorkerSoot @Inject constructor(
        val applicationClasses: String,
        val methodName: String,
        val classPath: String,
        val outputDirectory: String) : Runnable {

    override fun run() {
        Files.createDirectories(Paths.get(outputDirectory))
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
            PackManager.v().getPack("wjtp").add(
                    Transform("wjtp.myTransform", object : SceneTransformer() {
                        override fun internalTransform(phaseName: String?, options: MutableMap<String, String>?) {
                            val targetMethod = Scene.v().grabMethod(methodName)
                            if (targetMethod == null) {
                                //Not in a method lets look for a class
                                val targetClass = Scene.v().getSootClass(methodName.substringAfter('<').substringBefore(':'))
                                if (targetClass == null)
                                    throw Exception("Target method nor class were found. The signature used was $methodName.")
                                else
                                    processClassCallGraph(targetClass)
                            } else
                                processMethodCallGraph(targetMethod)
                        }
                    }))
        }catch (ex:Exception) {}
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
            methodsToProcess.forEach {
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
            methodsToProcess.forEach {
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
            val pattern = "^${targetMethod.declaringClass.name.replace(".", "\\.")}\\.${targetMethod.name}"
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
        if (c == null) return false
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
            (it as VisibilityAnnotationTag).annotations.any { testAnnotationTag == it.type }
        }
    }
}