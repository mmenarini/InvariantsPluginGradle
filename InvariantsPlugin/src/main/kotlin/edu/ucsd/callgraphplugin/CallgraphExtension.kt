package edu.ucsd.callgraphplugin

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import java.io.File

open class CallgraphExtension(objects: ObjectFactory) {
    private val customData = Configuration(objects.property(String::class.java),
            objects.property(String::class.java))
    var outFileDir: Property<File> = objects.property(File::class.java)
    fun getCustomData(): Configuration { return customData }
    fun customData(action: Action<in Configuration>) { action.execute(customData) }
}