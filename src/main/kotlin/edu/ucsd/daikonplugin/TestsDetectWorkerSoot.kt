package edu.ucsd.daikonplugin

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import soot.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Inject

interface TestsDetectParameters : WorkParameters {
    val addedClasses: MutableList<String>
    val changedClasses: MutableList<String>
    val removedClasses: MutableList<String>
    var classPath: String
    var outputFile: File
    var outputDir: Path
}
open abstract class TestsDetectWorkerSoot @Inject constructor() : WorkAction<TestsDetectParameters> {

    private val utils = TestsResultsUtils()

    override fun execute() {
        soot.G.reset()
        Files.createDirectories(parameters.outputDir)
        val argsPar = mutableListOf(//"-pp",
                /*"-w",*/ "-allow-phantom-refs", "-ice",
                //"-p", "bb", "enabled:false",
                //"-p", "tag", "enabled:false",
                "-p", "jtp", "enabled:true",
                "-f", "n",
                "-cp", parameters.classPath,
                "-d", parameters.outputDir.toAbsolutePath().toString()
        )

        var testMethodsList = mutableSetOf<String>()
        var testClassesSet = mutableSetOf<String>()
        val file = parameters.outputFile

        utils.LoadTestMethodsList(testClassesSet, testMethodsList, file)

        parameters.removedClasses.forEach {
            val classname = utils.classFilenameToClassname(it)
            if (classname!="") {
                testClassesSet.remove(classname)
                testMethodsList.removeAll { it.startsWith("<"+classname) }
            }
        }
        parameters.changedClasses.forEach {
            val classname = utils.classFilenameToClassname(it)
            if (classname!="") {
                testClassesSet.remove(classname)
                testMethodsList.removeAll { it.startsWith("<"+classname) }
            }
        }
        val args = mutableListOf<String>()
        parameters.changedClasses.forEach { args.add(utils.classFilenameToClassname(it)) }
        parameters.addedClasses.forEach { args.add(utils.classFilenameToClassname(it)) }
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
                            if ((utils.isInstanceOfClass(declClass,"junit.framework.TestCase") && method.name.startsWith("test")) ||
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