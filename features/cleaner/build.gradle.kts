import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// features:cleaner —— 清理 + 文件管理的共享功能层（复刻模式的品牌应用依赖它）：扫描、删除、文件浏览操作、提醒规则、各页面状态。
// 从 Orbit Clean 复制并去掉品牌值（文案、配色、场景都由品牌应用提供）；Orbit Clean 自己不依赖这个模块
plugins {
    id("appbase.core-library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.appbase.feature.cleaner"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:storage"))

    api(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
