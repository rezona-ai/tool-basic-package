import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// core:storage —— 文件夹授权（SAF）的文件读写：FolderAccess、FileStore、文件模型、打开/分享
plugins {
    id("appbase.core-library")
}

android {
    namespace = "com.appbase.core.storage"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:common"))

    testImplementation(libs.junit)
}
