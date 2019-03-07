package edu.ucsd.daikonplugin

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import java.nio.file.Files


open class DaikonAfterTest : DefaultTask() {
    @Internal
    var inputFile = project.objects.fileProperty()

    @OutputFile
    var outputFile = project.objects.fileProperty()

    @TaskAction
    internal fun daikonAfterTest() {
        if (inputFile.isPresent && inputFile.get().asFile.exists())
            inputFile.get().asFile.renameTo(outputFile.get().asFile)
    }
}
