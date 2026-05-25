// JVM library so the SDK works in Android apps AND server-side Kotlin / KMP
// projects. Consumers depend on it via JitPack, GitHub Packages, or Maven
// Central — `info.scoo-va:webhooks:<version>`.
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    `maven-publish`
    signing
    `java-library`
}

group = "info.scoo-va"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform {
        // kotlin-test on JVM uses the JUnit Platform engine.
    }
}

// Maven Central requires source + javadoc jars alongside the main jar; the
// `java-library` plugin plus these two lines satisfy that without a shadow-jar
// step.
java {
    withSourcesJar()
    withJavadocJar()
}

// ─── Publishing ──────────────────────────────────────────────────────────
// None of these targets require credentials at build time — only at
// `publish` task time.
//   - JitPack       → push a GitHub tag, JitPack builds on demand.
//   - GitHubPackages → GITHUB_ACTOR + GITHUB_TOKEN PAT with `write:packages`.
//   - MavenCentral  → OSSRH_USERNAME / OSSRH_PASSWORD + GPG key on the path.
publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])

            groupId = "info.scoo-va"
            artifactId = "webhooks"
            version = project.version.toString()

            pom {
                name.set("Scoova Webhooks SDK (Android / JVM)")
                description.set("Standalone Kotlin client for Scoova webhook subscriptions plus HMAC-SHA256 signature verification.")
                url.set("https://github.com/Scoova/scoova-webhooks-android")

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("scoova")
                        name.set("Scoova")
                        email.set("info@scoo-va.info")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/Scoova/scoova-webhooks-android.git")
                    developerConnection.set("scm:git:ssh://github.com:Scoova/scoova-webhooks-android.git")
                    url.set("https://github.com/Scoova/scoova-webhooks-android")
                }
            }
        }
    }

    repositories {
        // GitHub Packages.
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Scoova/scoova-webhooks-android")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as? String ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key") as? String ?: ""
            }
        }

        // Maven Central via the Sonatype OSSRH s01 staging.
        maven {
            name = "MavenCentral"
            val releasesUrl  = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl
            credentials {
                username = System.getenv("OSSRH_USERNAME") ?: project.findProperty("ossrh.username") as? String ?: ""
                password = System.getenv("OSSRH_PASSWORD") ?: project.findProperty("ossrh.password") as? String ?: ""
            }
        }
    }
}

// GPG signing — only required when actually publishing to Maven Central, so
// a developer who only wants to build locally does not need a GPG key.
signing {
    isRequired = gradle.taskGraph.hasTask("publishReleasePublicationToMavenCentralRepository")
    sign(publishing.publications["release"])
}
