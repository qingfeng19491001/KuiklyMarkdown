plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.1.21"
    id("com.android.library")
    id("maven-publish")
}

group = findProperty("groupId")?.toString() ?: "com.tencent.kuiklybase"
version = findProperty("mavenVersion")?.toString() ?: System.getenv("kuiklyBizVersion") ?: "1.0.0"

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
        publishLibraryVariants("release")
    }

    js(IR) {
        browser()
    }

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    macosArm64()
    macosX64()

    sourceSets {
        val commonMain by getting {
            val coreVersion = findProperty("kuiklyCoreVersion")?.toString() ?: Version.getKuiklyVersion()
            dependencies {
                compileOnly("com.tencent.kuikly-open:core:${coreVersion}")
                compileOnly("com.tencent.kuikly-open:core-annotations:${coreVersion}")
                compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
                compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val androidMain by getting {
            val coreVersion = findProperty("kuiklyCoreVersion")?.toString() ?: Version.getKuiklyVersion()
            dependencies {
                compileOnly("com.tencent.kuikly-open:core-render-android:${coreVersion}")
                api("ru.noties:jlatexmath-android:0.2.0")
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val macosX64Main by getting
        val macosArm64Main by getting

        val nativeMain by creating {
            dependsOn(commonMain)
        }

        val iosMain by creating {
            dependsOn(nativeMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }

        val macosMain by creating {
            dependsOn(nativeMain)
            macosX64Main.dependsOn(this)
            macosArm64Main.dependsOn(this)
        }
    }
}

android {
    namespace = "com.tencent.kuiklybase.markdown"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
}

publishing {
    repositories {
        maven {
            val repoUrl = findProperty("mavenRepoUrl")?.toString()
                ?: findProperty("MAVEN_REPO_URL")?.toString()
            if (repoUrl != null) {
                url = uri(repoUrl)
            }
            if (!url.scheme.equals("file", ignoreCase = true)) {
                credentials {
                    username = findProperty("mavenUsername")?.toString()
                        ?: System.getenv("mavenUserName") ?: ""
                    password = findProperty("mavenPassword")?.toString()
                        ?: System.getenv("mavenPassword") ?: ""
                }
            }
        }
    }
}
