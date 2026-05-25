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
version = "1.0.2"

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

            groupId    = "info.scoo-va"
            artifactId = "scoova-webhooks-android"
            version    = project.version.toString()

            pom {
                name.set("Scoova Webhooks SDK (Android / JVM)")
                description.set("Webhook subscription CRUD + HMAC-SHA256 signature verification.")
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
        // GitHub Packages — works immediately in Actions via GITHUB_TOKEN.
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Scoova/scoova-webhooks-android")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as? String ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key") as? String ?: ""
            }
        }

        // Local staging dir. `publishReleasePublicationToLocalStagingRepository`
        // writes the signed Maven layout here; the publish-to-central-portal.sh
        // script zips it and uploads to Sonatype Central Portal.
        maven {
            name = "LocalStaging"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

// In-memory PGP signing — required by Maven Central. SIGNING_KEY is the
// ASCII-armored secret key; SIGNING_PASSWORD is optional (current Scoova
// release key is passphrase-less). When absent (local builds, GitHub
// Packages), signing is skipped.
signing {
    val signingKey: String? = System.getenv("SIGNING_KEY")
    val signingPassword: String = System.getenv("SIGNING_PASSWORD") ?: ""
    isRequired = signingKey != null
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["release"])
    }
}