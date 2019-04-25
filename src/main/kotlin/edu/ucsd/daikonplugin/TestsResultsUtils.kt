package edu.ucsd.daikonplugin

import soot.MethodOrMethodContext
import soot.Scene
import soot.SootClass
import soot.SootMethod
import soot.tagkit.VisibilityAnnotationTag
import java.io.File
import java.lang.NullPointerException
import java.nio.file.Paths

class TestsResultsUtils {
    fun getPatternDollarNum() = """\$\d+"""
    fun getPatternDollar() = """\$"""
    private var regexDollarNum = Regex(getPatternDollarNum())
    private var regexDollar = Regex(getPatternDollar())
    fun classFilenameToClassname(name: String):String {
        var name = name.replace("/",".")
        if (!name.endsWith(".class")) return ""
        name = name.substringBefore(".class")
        name = regexDollarNum.replace(name,"")
        name = regexDollar.replace(name,".")
        return name
    }
    fun LoadTestMethodsList(testClass: MutableSet<String>, testMethods: MutableSet<String>, file : File){
        if (!file.exists()) return
        System.out.println("Reading testEntryPoints.txt")
        file.readLines().forEach {
            if (it.startsWith("C:"))
                testClass.add(it.substring(2))
            else if (it.startsWith("M:"))
                testMethods.add(it.substring(2))
        }
        System.out.println("testEntryPoints.txt read.")
    }
    fun OutputTestMethodsList(testClass: Set<String>, testMethods: Set<String>, file: File){
        System.out.println("Writing testEntryPoints.txt, will contain ${testClass.count()} classes " +
                "and ${testMethods.count()} methods")
        file.printWriter().use { out ->
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
    fun isInstanceOfClass(thisclass: SootClass, signature: String):Boolean {
        if(thisclass.name==signature)
            return true
        if(thisclass.hasSuperclass())
            return isInstanceOfClass(thisclass.superclass, signature)
        return false
    }
    fun isAnnotatedTest(sootMethod: SootMethod): Boolean{
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
/*
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
    private fun processMethodCallGraph(targetMethod: SootMethod) {
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
*/
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