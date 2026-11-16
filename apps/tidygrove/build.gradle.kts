import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Tidy Grove 品牌应用（复刻模式：清理 + 文件管理，功能来自 :features:cleaner）。
// 包名、版本、名称、链接、广告 ID、签名、所属账号都在同目录的 brand.properties，
// 由 build-logic 的 appbase.brand-app 注入（release 前做 validateBrand，打 AAB 前做 validateUpload + 账号串号校验）
plugins {
    id("appbase.brand-app")
}

android {
    // 代码包名与 R 类所在命名空间，和上架包名（brand.properties 的 APPLICATION_ID）无关
    namespace = "com.appbase.tidygrove"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ads"))
    implementation(project(":core:storage"))
    implementation(project(":core:ui"))
    implementation(project(":features:cleaner"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    // 文件缩略图（图片、视频帧）
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // 广告 SDK 由 :core:ads 引入

    testImplementation(libs.junit)
}
