package edu.ucsd.callgraphplugin

import org.gradle.api.Plugin
import org.gradle.api.Project

open class CallgraphPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("callGraphConfig",
                CallgraphExtension::class.java, project.objects)
        val customData = extension.getCustomData()
        project.tasks.register("callgraph", Callgraph::class.java) {task ->
            task.message.set("Hello")
            task.recipient.set("Massi")

        }
//
//                customData.message,
//                customData.recipient,
//                extension.outFileDir)
    }
}
