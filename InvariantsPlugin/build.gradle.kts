//import edu.ucsd.daikonplugin.DaikonExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "1.3.20"
}

group = "edu.ucsd.invariants"
version = "0.1-beta"

gradlePlugin {
    plugins {
        create("invariantsPlugin") {
            id = "invariants-plugin"
            implementationClass = "edu.ucsd.daikonplugin.DaikonPlugin"
        }
    }

}

repositories {
    mavenCentral()
}

dependencies {
    compile("org.jetbrains.kotlin:kotlin-reflect:1.3.20")
    compile(kotlin("stdlib-jdk8"))
}


val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
