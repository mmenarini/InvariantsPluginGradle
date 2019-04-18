package edu.ucsd.daikonplugin

import soot.*
import soot.tagkit.VisibilityAnnotationTag
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.inject.Inject
import kotlin.collections.HashSet
import soot.tagkit.AnnotationTag
import java.lang.NullPointerException
import kotlin.collections.ArrayList


open class TestsWorkerSoot @Inject constructor(
        val applicationClasses: String,
        val classPath: String,
        val outputDirectory: String) : Runnable {

    override fun run() {
        soot.G.reset()
        Files.createDirectories(Paths.get(outputDirectory))
        val args = mutableListOf(//"-pp",
                /*"-w",*/ "-allow-phantom-refs", "-ice",
                //"-p", "bb", "enabled:false",
                //"-p", "tag", "enabled:false",
                "-p", "jtp", "enabled:true",
                "-f", "n",
                "-cp", classPath,
                "-d", outputDirectory
        )

        var testMethodsList = ArrayList<String>()
        var testClassesSet = HashSet<String>()
        applicationClasses.split(":")
                .forEach { args.addAll(arrayOf("-process-dir", it)) }
        try {
            val jtp = PackManager.v().getPack("jtp")
            //System.err.println("Removing old TestFinder")
            jtp.removeAll { true }
            //System.err.println("Removing old TestFinder Done")
            jtp.add(
                    Transform("jtp.TestFinder", object : BodyTransformer() {
                        override fun internalTransform(b: Body?, phaseName: String?, options: MutableMap<String, String>?) {
                            if (b==null) return
                            var method = b.method
                            var declClass = method?.declaringClass
                            if(declClass==null || method==null) return
                            if (isInstanceOfClass(declClass,"junit.framework.TestCase") ||
                                    isAnnotatedTest(method))
                            {
                                var className = declClass?.name
                                var methodName = method?.name
                                if(className!=null && methodName!=null) {
                                    testClassesSet.add(className)
                                    testMethodsList.add(method.signature)
                                }
                            }

                            /*val targetMethod = Scene.v().grabMethod(methodName)
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
                                processMethodCallGraph(targetMethod)*/
                        }
                    }))
        }catch (ex:Exception) {
            System.err.println(ex.message)
            ex.printStackTrace()
        }
        soot.Main.main(args.toTypedArray())
        OutputTestMethodsList(testClassesSet, testMethodsList)
        
    }
    private fun isAnnotatedTest(sootMethod: SootMethod): Boolean{
        val tag = sootMethod.getTag("VisibilityAnnotationTag") as VisibilityAnnotationTag?
        tag?.annotations?.forEach {
            val annotationType = it.type
            if (annotationType.equals("Landroid/webkit/JavascriptInterface;") ||
                    annotationType.equals("Lorg/junit/BeforeClass;") ||
                    annotationType.equals("Lorg/junit/AfterClass;") ||
                    annotationType.equals("Lorg/junit/Before;") ||
                    annotationType.equals("Lorg/junit/After;") ||
                    annotationType.equals("Lorg/junit/Test;")) {
                return true
            }
        }
        return false
    }
    private fun OutputTestMethodsList(testClass: Set<String>, testMethods: List<String>){
        System.out.println("Writing testEntryPoints.txt, will contain ${testClass.count()} classes " +
                "and ${testMethods.count()} methods")
        Paths.get(outputDirectory).resolve(Tests.OUTPUT_FILE_NAME).toFile().printWriter().use { out ->
            try {
                testClass.forEach {
                    out.println("C:${it}")
                }
                testMethods.toTypedArray().forEach {
                    out.println("M:${it}")
                }
            } catch(e: NullPointerException) {
                e.printStackTrace()
            }
        }
        System.out.println("testEntryPoints.txt written.")
    }
    private fun isInstanceOfClass(thisclass: SootClass, signature: String):Boolean {
        if(thisclass.name==signature)
            return true
        if(thisclass.hasSuperclass())
            return isInstanceOfClass(thisclass.superclass, signature)
        return false
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