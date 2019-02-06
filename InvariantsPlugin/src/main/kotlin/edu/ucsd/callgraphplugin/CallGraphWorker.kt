package edu.ucsd.callgraphplugin

import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Inject



open class CallGraphWorker @Inject constructor(val outputDirectory: String) : Runnable {

    override fun run() {
        val outputDirectory = Paths.get(this.outputDirectory)
        if (!Files.exists(outputDirectory)) {
            Files.createDirectory(outputDirectory)
        }
        if (!Files.isDirectory(outputDirectory))
            throw Exception(outputDirectory.toString() + " is not a directory")

        outputDirectory.resolve("worker_classpath.txr").toFile().printWriter().use { out ->
            val roots = System::class.java.classLoader.getResources("")
            roots.iterator().forEach {
                out.println(Paths.get(it.toURI()).toAbsolutePath().toString())
            }
        }

    }
}