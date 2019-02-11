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
    maven{
        url = uri("https://soot-build.cs.uni-paderborn.de/nexus/repository/swt-upb/")
    }
    jcenter {
        content {
            // this repository contains everything BUT artifacts with group starting with "my.company"
            excludeGroupByRegex("ca\\.mcgill.*")
        }
    }
}

dependencies {
    compile("org.jetbrains.kotlin:kotlin-reflect:1.3.20")
    compile(kotlin("stdlib-jdk8"))
    compile("com.github.javaparser:javaparser-symbol-solver-core:3.11.0")
    compileOnly(project.layout.files("/home/mmenarini/daikon-5.7.2/daikon.jar"))
    compile("ca.mcgill.sable:soot:3.2.0")
    //compileOnly(project.layout.files("/home/mmenarini/Dev/daikon/daikon.jar"))
}


val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
