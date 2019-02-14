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

        PackManager.v().getPack("wjtp").add(
                Transform("wjtp.myTransform", object : SceneTransformer() {
                    override fun internalTransform(phaseName: String?, options: MutableMap<String, String>?) {
                        try {
                            val targetMethod = Scene.v().grabMethod(methodName)
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
                            Paths.get(outputDirectory).resolve("testSet.txt").toFile().printWriter().use { out ->
                                testMethods.forEach {
                                    out.println("${it.method().declaringClass.name}.${it.method().name}")
                                }
                            }
                            Paths.get(outputDirectory).resolve("path.txt").toFile().printWriter().use { out ->
                                out.println("^${targetMethod.declaringClass.name.replace(".", "\\.")}\\.${targetMethod.name}")
                            }
                        } catch(e: Exception) {
                            System.err.println(e.message)
                            e.printStackTrace()
                        }
                    }
                }))
        soot.Main.main(args.toTypedArray())
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