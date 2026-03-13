import org.jetbrains.kotlin.gradle.dsl.JvmTarget

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

repositories {
    google()

    mavenCentral()

    maven("https://jitpack.io")
}

group = "one.wabbit"
version = "0.2.0"

plugins {
    id("com.android.kotlin.multiplatform.library")

    kotlin("multiplatform")

    kotlin("plugin.serialization")

    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlinx.kover")
    id("maven-publish")

    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    coordinates("one.wabbit", "kotlin-openai-schemas", "0.2.0")
    publishToMavenCentral()
    signAllPublications()
    pom {
        name.set("kotlin-openai-schemas")
        description.set("kotlin-openai-schemas")
        url.set("https://github.com/wabbit-corp/kotlin-openai-schemas")
        licenses {
            license {
                name.set("GNU Affero General Public License v3.0 or later")
                url.set("https://spdx.org/licenses/AGPL-3.0-or-later.html")
            }
        }
        developers {
            developer {
                id.set("wabbit-corp")
                name.set("Wabbit Consulting Corporation")

                email.set("wabbit@wabbit.one")

            }
        }
        scm {
            url.set("https://github.com/wabbit-corp/kotlin-openai-schemas")
            connection.set("scm:git:git://github.com/wabbit-corp/kotlin-openai-schemas.git")
            developerConnection.set("scm:git:ssh://git@github.com/wabbit-corp/kotlin-openai-schemas.git")
        }
    }
}

val localPublishRequested =
    gradle.startParameter.taskNames.any { taskName -> "MavenLocal" in taskName }

if (localPublishRequested) {
    tasks.withType<org.gradle.plugins.signing.Sign>().configureEach {
        enabled = false
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")

    }
    applyDefaultHierarchyTemplate()

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        testRuns["test"].executionTask.configure {
            jvmArgs("-ea")
        }
    }

    androidLibrary {
        namespace = "one.wabbit.openaischemas"
        compileSdk = 34
        minSdk = 26
    }

    iosArm64()

    iosSimulatorArm64()

    macosArm64("hostNative")

    targets.withType(KotlinNativeTarget::class.java).configureEach {
        binaries.framework {
            baseName = "OpenaiSchemas"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.aallam.openai:openai-client:4.0.1")

                implementation("one.wabbit:kotlin-data-ref:2.0.0")

            }

        }

        val commonTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test:2.3.10")

            }

        }

    }
}

val configuredVersionString = version.toString()

tasks.register("printVersion") {
    inputs.property("configuredVersion", configuredVersionString)
    doLast {
        println(inputs.properties["configuredVersion"])
    }
}

tasks.register("assertReleaseVersion") {
    inputs.property("configuredVersion", configuredVersionString)
    doLast {
        val versionString = inputs.properties["configuredVersion"].toString()
        require(!versionString.endsWith("+dev-SNAPSHOT")) {
            "Release publishing requires a non-snapshot version, got $versionString"
        }
        val refType = System.getenv("GITHUB_REF_TYPE") ?: ""
        val refName = System.getenv("GITHUB_REF_NAME") ?: ""
        if (refType == "tag" && refName.isNotBlank()) {
            val expectedTag = "v$versionString"
            require(refName == expectedTag) {
                "Git tag $refName does not match project version $versionString"
            }
        }
    }
}

tasks.register("assertSnapshotVersion") {
    inputs.property("configuredVersion", configuredVersionString)
    doLast {
        val versionString = inputs.properties["configuredVersion"].toString()
        require(versionString.endsWith("+dev-SNAPSHOT")) {
            "Snapshot publishing requires a +dev-SNAPSHOT version, got $versionString"
        }
        require((System.getenv("GITHUB_REF_TYPE") ?: "") != "tag") {
            "Snapshot publishing must not run from a tag ref"
        }
    }
}

tasks.withType<Test>().configureEach {
    jvmArgs("-ea")
}

dokka {
    moduleName.set("kotlin-openai-schemas")
    dokkaPublications.html {
        suppressInheritedMembers.set(true)
        failOnWarning.set(true)
    }

    dokkaSourceSets.configureEach {
        if (name == "commonMain") {
            val dokkaModuleFile = file("docs/dokka-module.md")
            if (dokkaModuleFile.exists()) {
                includes.from(dokkaModuleFile)
            }
        }

        sourceLink {
            localDirectory.set(file("src"))
            remoteUrl("https://github.com/wabbit-corp/kotlin-openai-schemas/tree/master/src")
            remoteLineSuffix.set("#L")
        }

    }

    pluginsConfiguration.html {
        footerMessage.set("(c) Wabbit Consulting Corporation")
    }
}
