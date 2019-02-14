package edu.ucsd.daikonplugin

import org.gradle.api.model.ObjectFactory
import java.nio.file.Path
import java.nio.file.Paths


open class DaikonExtension(objects: ObjectFactory) {
    private val DEFAULT_DAIKON_DIR = System.getenv("DAIKONDIR")
    private val DEFAULT_DAIKON_JAR = "daikon.jar"

    val sourceFile = objects.fileProperty()
    val sourceFileLineNumber = objects.property(Int::class.java)

    val pattern = objects.property(String::class.java)
    val daikonOutputDirectory = objects.directoryProperty()
    val callgraphOutputDirectory = objects.directoryProperty()
    val invariantsOutputDirectory = objects.directoryProperty()

    val daikonInstallationPath = objects.property(String::class.java)
    val daikonJarFileName = objects.property(String::class.java)

    fun getDaikonJarPath():Path {
        return try {
            if (!daikonInstallationPath.isPresent && DEFAULT_DAIKON_DIR == "")
                throw Exception("Cannot run daikon because environment variable DAIKONDIR and property daikonInstallationPath not set. " +
                        "You need to set at least one of them to be able to find daikon.jar")
            val daikonDir = if (!daikonInstallationPath.isPresent) DEFAULT_DAIKON_DIR else daikonInstallationPath.get()
            val daikonJar = if (!daikonJarFileName.isPresent) DEFAULT_DAIKON_JAR else daikonJarFileName.get()
            if (daikonDir!=null)
                Paths.get(daikonDir).resolve(daikonJar)
            else
                Paths.get("")
        } catch(e:Exception){
            e.printStackTrace()
            Paths.get("")
        }
    }

}