package edu.ucsd.daikonplugin

import soot.*
import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Inject
import kotlin.collections.HashSet

open class TestsWorkerSoot @Inject constructor(
        val addedClasses: List<String>,
        val changedClasses: List<String>,
        val removedClasses: List<String>,
        val classPath: String,
        val outputDirectory: String) : Runnable {

    private val utils = TestsResultsUtils()

    override fun run() {
        soot.G.reset()
        Files.createDirectories(Paths.get(outputDirectory))
        val argsPar = mutableListOf(//"-pp",
                /*"-w",*/ "-allow-phantom-refs", "-ice",
                //"-p", "bb", "enabled:false",
                //"-p", "tag", "enabled:false",
                "-p", "jtp", "enabled:true",
                "-f", "n",
                "-cp", classPath,
                "-d", outputDirectory
        )

        var testMethodsList = mutableSetOf<String>()
        var testClassesSet = mutableSetOf<String>()
        val file = Paths.get(outputDirectory).resolve(Tests.OUTPUT_FILE_NAME).toFile()
        utils.LoadTestMethodsList(testClassesSet, testMethodsList, file)
        removedClasses.forEach {
            val classname = utils.classFilenameToClassname(it)
            if (classname!="") {
                testClassesSet.remove(classname)
                testMethodsList.removeAll { it.startsWith("<"+classname) }
            }
        }
        changedClasses.forEach {
            val classname = utils.classFilenameToClassname(it)
            if (classname!="") {
                testClassesSet.remove(classname)
                testMethodsList.removeAll { it.startsWith("<"+classname) }
            }
        }
        val args = mutableListOf<String>()
        changedClasses.forEach { args.add(utils.classFilenameToClassname(it)) }
        addedClasses.forEach { args.add(utils.classFilenameToClassname(it)) }
        args.addAll(argsPar)
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
                            if(declClass==null || method==null || method.isConstructor) return
                            if (utils.isInstanceOfClass(declClass,"junit.framework.TestCase") ||
                                    utils.isAnnotatedTest(method))
                            {
                                var className = declClass?.name
                                var methodName = method?.name
                                if(className!=null && methodName!=null) {
                                    testClassesSet.add(className)
                                    testMethodsList.add(method.signature)
                                }
                            }
                        }
                    }))
        }catch (ex:Exception) {
            System.err.println(ex.message)
            ex.printStackTrace()
        }
        soot.Main.main(args.toTypedArray())
        utils.OutputTestMethodsList(testClassesSet, testMethodsList, file)
        
    }
}