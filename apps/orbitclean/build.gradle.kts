import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Orbit Clean 品牌应用。包名、版本、名称、链接、广告 ID、签名都在同目录的 brand.properties，
// 由 build-logic 的 appbase.brand-app 注入（release 前还会做 validateBrand 校验）
plugins {
    id("appbase.brand-app")
}

android {
    // 代码包名与 R 类所在命名空间，和上架包名（brand.properties 的 APPLICATION_ID）无关
    namespace = "com.orbitclean.app"
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
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    implementation(libs.applovin.sdk)
    // GDPR 授权弹窗：MAX 的隐私授权流程基于 Google UMP（需在 AdMob 后台配置 GDPR 消息，并在清单中声明 AdMob App ID）
    implementation(libs.google.ump)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Google AdMob：未配置 MAX 时直接展示 AdMob 广告（见 AdMobAdProvider）
    implementation(libs.play.services.ads)
    // Adjust 归因与广告收入上报；Install Referrer 用于 Google Play 安装来源归因（广告 ID 由 play-services-ads 间接引入）
    implementation(libs.adjust.android)
    implementation(libs.android.installreferrer)
    // Google Play 应用内评分（In-App Review）：清理完成页在合适时机请求，见 core:common 的 ReviewPrompt
    implementation(libs.play.review.ktx)
    // 各广告平台 Adapter：按 MAX 后台启用的 Network 添加，版本以 MAX Integration Manager 为准。
    // 注意：加入 Google Adapter 时必须在 AndroidManifest 配置 AdMob App ID，否则启动即崩溃。
    // implementation("com.applovin.mediation:google-adapter:25.5.0.0")
    // implementation("com.applovin.mediation:facebook-adapter:6.22.0.1")
    // implementation("com.applovin.mediation:bytedance-adapter:8.4.0.5.0")
    // implementation("com.applovin.mediation:unityads-adapter:4.21.0.1")
    // implementation("com.applovin.mediation:mintegral-adapter:17.2.21.0")
}
