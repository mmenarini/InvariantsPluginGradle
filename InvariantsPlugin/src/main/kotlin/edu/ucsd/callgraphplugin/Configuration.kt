package edu.ucsd.callgraphplugin

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input


data class Configuration (
        @Input var message: Property<String>,
        @Input var recipient: Property<String>)