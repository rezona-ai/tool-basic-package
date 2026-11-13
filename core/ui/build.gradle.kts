import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// core:ui —— 主题骨架、通用组件、文件夹授权页、通用设置页、启动流程（StartupActivity）
plugins {
    id("appbase.core-library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.appbase.core.ui"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:ads"))
    api(project(":core:storage"))

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.coil.compose)
}
