import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// {{APP_NAME}} 品牌应用。包名、版本、名称、链接、广告 ID、签名都在同目录的 brand.properties，
// 由 build-logic 的 appbase.brand-app 注入（release 前做 validateBrand，打 AAB 前做 validateUpload）
plugins {
    id("appbase.brand-app")
}

android {
    // 代码包名与 R 类所在命名空间，和上架包名（brand.properties 的 APPLICATION_ID）无关
    namespace = "{{CODE_PACKAGE}}"
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 广告 SDK 由 :core:ads 引入；这里只放本应用核心功能需要的依赖

    testImplementation(libs.junit)
}
