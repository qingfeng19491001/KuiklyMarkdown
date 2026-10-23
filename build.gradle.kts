plugins {
    //trick: for the same plugin versions in all sub-modules
    id("com.android.application").version("7.4.2").apply(false)
    id("com.android.library").version("7.4.2").apply(false)
    kotlin("android").version("2.1.21").apply(false)
    kotlin("multiplatform").version("2.1.21").apply(false)
    kotlin("plugin.compose").version("2.1.21").apply(false)
    id("com.google.devtools.ksp").version("2.1.21-2.0.1").apply(false)

}

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        }
    }
    dependencies {
        // AGP 7.4 自带 D8 无法解析 Kotlin 2.1 metadata（ERROR:D8: com.android.tools.r8.kotlin.H）
        classpath("com.android.tools:r8:8.7.18")
        classpath(BuildPlugin.kuikly)
    }
}
