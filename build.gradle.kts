import org.gradle.jvm.toolchain.JvmVendorSpec
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.21"
    java
    `maven-publish`
}

group = "com.github.knk190001"
version = "0.1.8"

repositories {
    mavenCentral()
}
java {
    toolchain {
        vendor.set(JvmVendorSpec.ADOPTIUM)
        languageVersion.set(JavaLanguageVersion.of(19))
    }
}

kotlin {
    sourceSets.all {
        languageSettings {
            languageVersion = "2.0"
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "19"
        freeCompilerArgs = listOf("-Xjvm-default=all-compatibility")
    }
}


dependencies {
    implementation(kotlin("stdlib"))
    api("com.squareup:kotlinpoet:1.12.0")
    api("com.headius:invokebinder:1.13")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    api("net.java.dev.jna:jna:5.12.1")
    api("net.java.dev.jna:jna-platform:5.12.1")
    implementation("com.47deg:memeid:0.7.0")
    implementation("com.beust:klaxon:5.5")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.knk190001"
            artifactId = "kotlin-winrt-generator"
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri("https://sfxdev-101802139621.d.codeartifact.us-west-2.amazonaws.com/maven/KotlinWinRT/")
            credentials {
                username = "aws"
                password = System.getenv("CODEARTIFACT_AUTH_TOKEN")
            }
        }
    }
}

tasks.withType<KotlinCompile>(){
    kotlinOptions.jvmTarget = "19"
//    jvmTarget = "19"
}
