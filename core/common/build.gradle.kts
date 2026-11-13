import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// core:common —— CoreServices / CoreConfig、格式化、应用内评分、本地提醒框架、跳出 App 标记
plugins {
    id("appbase.core-library")
}

android {
    namespace = "com.appbase.core.common"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.androidx.core.ktx)
    // ReminderSetup 暴露 ListenableWorker，品牌的提醒 Worker 也基于它
    api(libs.androidx.work.runtime)
    api(libs.kotlinx.coroutines.android)
    // Google Play 应用内评分（In-App Review）
    implementation(libs.play.review.ktx)

    testImplementation(libs.junit)
}
