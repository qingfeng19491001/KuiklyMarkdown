plugins {
    kotlin("multiplatform")
    // 必须应用 Compose 编译器插件，否则 @Composable 函数编译后缺少 Composer 参数，
    // 与 metadata 中的签名不一致，消费方运行时抛 NoSuchMethodError。
    kotlin("plugin.compose")
    id("com.android.library")
    id("maven-publish")
}

// 基础模块按 <version>-<kotlinVersion> 发布，wrapper 不带 kotlin 后缀，
// 两者在同一次构建中发布，所以 wrapper 版本号需要能与 mavenVersion 分开指定。
version = findProperty("COMPOSE_MAVEN_VERSION")?.toString()
    ?: findProperty("mavenVersion")?.toString()
    ?: findProperty("MAVEN_VERSION")?.toString()
    ?: "1.0.6"
group = findProperty("groupId")?.toString() ?: "com.tencent.kuiklybase"

publishing {
    repositories {
        maven {
            val repoUrl = findProperty("mavenRepoUrl")?.toString()
                ?: findProperty("MAVEN_REPO_URL")?.toString() ?: ""
            url = if (repoUrl.isNotBlank()) {
                uri(repoUrl)
            } else {
                uri(layout.buildDirectory.dir("repo"))
            }
            val user = findProperty("mavenUsername")?.toString()
                ?: System.getenv("mavenUserName") ?: ""
            val pass = findProperty("mavenPassword")?.toString()
                ?: System.getenv("mavenPassword") ?: ""
            // file:// 仓库不支持鉴权，仅远程仓库才带上凭据
            if (user.isNotBlank() && !url.scheme.equals("file", ignoreCase = true)) {
                credentials {
                    username = user
                    password = pass
                }
            }
        }
    }
}

kotlin {
    androidTarget {
        compilations.all { kotlinOptions { jvmTarget = "1.8" } }
        publishLibraryVariants("release")
    }
    js(IR) { browser(); binaries.library() }
    iosX64(); iosArm64(); iosSimulatorArm64()
    macosX64(); macosArm64()

    sourceSets {
        val commonMain by getting {
            val coreVersion = findProperty("kuiklyCoreVersion")?.toString()
                ?: Version.getKuiklyVersion()
            dependencies {
                api(project(":KuiklyMarkdown"))
                implementation("com.tencent.kuikly-open:core:${coreVersion}")
                implementation("com.tencent.kuikly-open:core-annotations:${coreVersion}")
                implementation("com.tencent.kuikly-open:compose:${coreVersion}")
            }
        }
        val commonTest by getting { dependencies { implementation(kotlin("test")) } }
        val androidMain by getting {
            dependencies {
                api("com.tencent.kuikly-open:core-render-android:${Version.getKuiklyVersion()}")
            }
        }
        val jsMain by getting { dependsOn(commonMain) }
        val iosX64Main by getting; val iosArm64Main by getting; val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this); iosArm64Main.dependsOn(this); iosSimulatorArm64Main.dependsOn(this)
        }
        val macosX64Main by getting; val macosArm64Main by getting
    }
}

android {
    namespace = "com.tencent.kuiklybase.markdown.compose"
    compileSdk = 34
    defaultConfig { minSdk = 21 }
}
