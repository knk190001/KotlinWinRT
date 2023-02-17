plugins {
    kotlin("jvm") version "1.6.21"
    java
    `maven-publish`
}

group = "org.github.knk190001"
version = "0.1.2"

repositories {
    mavenCentral()
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(16))
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    api("com.squareup:kotlinpoet:1.12.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.21")
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

