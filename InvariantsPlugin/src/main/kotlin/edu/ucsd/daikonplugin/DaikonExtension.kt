package edu.ucsd.daikonplugin

import edu.ucsd.daikonpl.Daikon
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths



open class DaikonExtension(objects: ObjectFactory) {
    val DEFAULT_DAIKON_DIR = System.getenv("DAIKONDIR")
    val DEFAULT_DAIKON_JAR = "daikon.jar"
    //val DAIKON_JAVA_SH = "/home/mmenarini/Dev/getty/src/main/sh/java-daikon.sh"

    val pattern: Property<String> = objects.property(String::class.java)
    val daikonOutputDirectory : DirectoryProperty = objects.directoryProperty()
    val callgraphOutputDirectory : DirectoryProperty = objects.directoryProperty()

    val daikonInstallationPath: Property<String> = objects.property(String::class.java)
    val daikonJarFileName: Property<String> = objects.property(String::class.java)
    //var pattern: String? = null

    //val daikonJavaScript: Property<String> = objects.property(String::class.java)

//    fun daikonJavaScriptResolved(): String {
//        if (daikonJavaScript.isPresent)
//            return daikonJavaScript.get()
//
//        return resolvePluginPath().resolve(
//                    if (System.getProperty("os.name").capitalize().startsWith("WIN"))
//                        "java-daikon.bat" else "java-daikon.sh").toString()
//    }

//    private fun resolvePluginPath():Path {
//        val src = Daikon::class.java.getProtectionDomain().getCodeSource()
//        if (src != null) {
//            val jar = src.getLocation()
//            var location = Paths.get(jar.toURI()).toAbsolutePath()
//            if (!Files.exists(location)) {
//                throw Exception("Cannot find the location of the Daikon Gradle Plugin")
//            }
//            if (Files.isRegularFile(location))
//                location=location.parent
//            return location
//        }
//        return Paths.get("")
//    }

//    fun getGradleWorkerJar():Path {
////        val src = GradleWorkerMain::class.java.getProtectionDomain().getCodeSource()
////        if (src != null) {
////            val jar = src.getLocation()
////            var location = Paths.get(jar.toURI()).toAbsolutePath()
////            return location
////        }
//        return Paths.get("")
//    }

    fun getDaikonJarPath():Path {
        try {
            if (!daikonInstallationPath.isPresent && DEFAULT_DAIKON_DIR == "")
                throw Exception("Cannot run daikon because environment variable DAIKONDIR and property daikonInstallationPath not set. " +
                        "You need to set at least one of them to be able to find daikon.jar")
            val daikonDir = if (!daikonInstallationPath.isPresent) DEFAULT_DAIKON_DIR else daikonInstallationPath.get()
            val daikonJar = if (!daikonJarFileName.isPresent) DEFAULT_DAIKON_JAR else daikonJarFileName.get()
            return Paths.get(daikonDir).resolve(daikonJar)
        } catch(e:Exception){
            e.printStackTrace()
            return Paths.get("")
        }
    }

}