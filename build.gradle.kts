plugins {
    kotlin("jvm") version "1.6.21"
    java
}

apply(plugin = "com.github.knk190001.gradle-code-generator-kotlin")
group = "org.github.knk190001"

version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val generatingSourceSet = sourceSets["mainGenerator"]!!
val generatingConfig = configurations[generatingSourceSet.implementationConfigurationName]!!
dependencies {
    implementation(kotlin("stdlib"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.21")
    implementation("net.java.dev.jna:jna:5.12.1")
    implementation("net.java.dev.jna:jna-platform:5.12.1")
    generatingConfig("net.java.dev.jna:jna:5.12.1")
    generatingConfig("com.47deg:memeid:0.7.0")
    generatingConfig("net.java.dev.jna:jna-platform:5.12.1")
    generatingConfig("com.beust:klaxon:5.5")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
buildscript {
    dependencies {
        classpath("com.github.knk190001:GradleCodeGenerator:1.0.5")
    }
}
