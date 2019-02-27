package edu.ucsd.daikonplugin

import org.gradle.api.model.ObjectFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


open class DaikonExtension(objects: ObjectFactory) {
    private val DEFAULT_DAIKON_DIR = System.getenv("DAIKONDIR")
    private val DEFAULT_DAIKON_JAR = "daikon.jar"

    val sourceFile = objects.fileProperty()
    val sourceFileLineNumber = objects.property(Int::class.java)
    val methodSignature = objects.property(String::class.java)

    val pattern = objects.property(String::class.java)
    val daikonOutputDirectory = objects.directoryProperty()
    val callgraphOutputDirectory = objects.directoryProperty()
    val invariantsOutputDirectory = objects.directoryProperty()

    val daikonInstallationPath = objects.property(String::class.java)
    val daikonJarFileName = objects.property(String::class.java)

    fun getDaikonJarPath():Path {
        try {
            val daikonJar = if (!daikonJarFileName.isPresent) DEFAULT_DAIKON_JAR else daikonJarFileName.get()
            if (!daikonInstallationPath.isPresent && (DEFAULT_DAIKON_DIR == null || DEFAULT_DAIKON_DIR == "")) {
                //Search in /opt/daikon and ~/daikon
                if (Files.exists(Paths.get(System.getProperty("user.home")).resolve("daikon").resolve(daikonJar)))
                    return Paths.get(System.getProperty("user.home")).resolve("daikon").resolve(daikonJar)
                if (Files.exists(Paths.get("/opt/daikon").resolve(daikonJar)))
                    return Paths.get("/opt/daikon").resolve(daikonJar)
                throw Exception("Cannot run daikon because environment variable DAIKONDIR and property daikonInstallationPath not set. " +
                        "You need to set at least one of them to be able to find daikon.jar")
            }
            val daikonDir = if (!daikonInstallationPath.isPresent) DEFAULT_DAIKON_DIR else daikonInstallationPath.get()
            if (daikonDir!=null)
                return Paths.get(daikonDir).resolve(daikonJar)
        } catch(e:Exception){
            e.printStackTrace()
        }
        return Paths.get("")
    }

}