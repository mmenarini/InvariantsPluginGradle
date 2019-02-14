package edu.ucsd.daikonplugin

import java.lang.StringBuilder
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Paths
import javax.inject.Inject



open class InvariantsWorker @Inject constructor(
    val pattern: String,
    val inputFile: String,
    val outputDirectory: String) : Runnable {

    private val splitter = "==========================================================================="
    private fun ExtractMethodName(s:String):String{
        return s.substringBefore(":::")
    }
    private fun FileSplitter(lines: Sequence<String>) : Sequence<String> {
        var tmp = StringBuilder("")
        var method = ""
        var lastLineSplit = false
        return sequence {
            lines.forEach {
                if (lastLineSplit) {
                    lastLineSplit = false
                    val curMethod = ExtractMethodName(it)
                    if(!curMethod.equals(method)) {
                        if (tmp.isNotEmpty())
                            yield(tmp.toString())
                        tmp.clear()
                    }
                    method=curMethod
                }
                if (it.equals(splitter)) {
                    lastLineSplit=true
                }
                else
                    tmp.appendln(it)
            }
        }
    }

    override fun run() {
        val inputFile = Paths.get(this.inputFile)
        val outputDirectory = Paths.get(this.outputDirectory)

        if(Files.notExists(inputFile))
            throw Exception("Input file ${this.inputFile} not found")

        Files.createDirectories(outputDirectory)

        val outputFilePath = outputDirectory.resolve("result.inv").toAbsolutePath()

        var printInvariants =  Class.forName("daikon.PrintInvariants")
        var printInvariantsMain = printInvariants.getMethod("main", Array<String>::class.java)
        printInvariantsMain.invoke(null,
                arrayOf("--output", outputFilePath.toString(), this.inputFile))
//        daikon.PrintInvariants.main(arrayOf("--output",
//                outputFilePath.toString(),
//                this.inputFile))

        outputFilePath.toFile().bufferedReader().use { input ->
            input.useLines { sequence ->
                FileSplitter(sequence).forEach { s ->
                    val subFolder =  Paths.get(ExtractMethodName(s).substringBefore("(").replace(".","/"))
                    val filename = subFolder.fileName.toString() +
                            if (ExtractMethodName(s).contains("("))
                                "(" + ExtractMethodName(s).substringAfter("(")
                            else ""
                    val fullDirectory = outputDirectory.resolve(subFolder.parent)
                    Files.createDirectories(fullDirectory)
                    fullDirectory.resolve(filename+".inv").toFile().printWriter().use {
                        it.print(s)
                    }
                }
            }
        }

    }
}