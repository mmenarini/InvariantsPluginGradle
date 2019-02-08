package edu.ucsd.daikonplugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import java.nio.file.Files


open class Daikon : DefaultTask() {

    @Internal
    lateinit var additionalClassPath: FileCollection
    @Internal
    lateinit var daikonJarPath: String
    @Input
    val daikonPattern: Property<String> = project.objects.property(String::class.java)

    @OutputFile
    //@Optional
    var outputFile = project.objects.fileProperty()

    @TaskAction
    internal fun doStuff() {
        //val dep  = project.dependencies.
        //project.dependencies.add("testImplementation",FileCollection(File("/home/mmenarini/Dev/daikon/daikon.jar"))
        if (!daikonPattern.isPresent)
            throw Exception("Cannot run daikon without a daikonPattern specified")
        project.tasks.withType(Test::class.java).configureEach { test ->

            test.maxParallelForks  = 1
            test.setForkEvery(0)

            val outputDirectoryPath = outputFile.get().asFile.toPath().parent
            val outputFilePath = outputFile.get().asFile.toPath()

            Files.createDirectories(outputDirectoryPath)
            val newScript = outputDirectoryPath.resolve("java-daikon.sh")

            //Create a new script
            javaClass.getResourceAsStream("/java-daikon.sh").bufferedReader().use{ input ->
                if (Files.exists(newScript)) Files.delete(newScript)
                newScript.toFile().printWriter().use{ output ->
                    input.lines().forEach{ line ->
                        if (line.contains("@OUTPUT_FILE@"))
                            output.println(line.replace("@OUTPUT_FILE@", outputFilePath.toString()))
                        else if (line.contains("@DAIKON_JAR@"))
                            output.println(line.replace("@DAIKON_JAR@", daikonJarPath))
                        else if (line.contains("@PATTERN@"))
                            output.println(line.replace("@PATTERN@", daikonPattern.get()))
                        else
                            output.println(line)
                    }
                }
                if (!Files.isExecutable(newScript))
                    newScript.toFile().setExecutable(true, true)
            }
            test.executable = newScript.toString()
            test.classpath = test.classpath.plus(additionalClassPath)
        }
    }
}
