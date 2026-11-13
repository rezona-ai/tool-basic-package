import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// core:ads —— 广告（AdMob / AppLovin MAX / 占位）、频控、返回插屏、Adjust 归因与收入上报
plugins {
    id("appbase.core-library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.appbase.core.ads"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:common"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    // Google AdMob：未配置 MAX 时直接展示 AdMob 广告（见 AdMobAdProvider）
    implementation(libs.play.services.ads)
    // GDPR 授权弹窗：基于 Google UMP（需在 AdMob 后台配置 GDPR 消息，并在清单中声明 AdMob App ID）
    implementation(libs.google.ump)
    implementation(libs.applovin.sdk)
    // Adjust 归因与广告收入上报；Install Referrer 用于 Google Play 安装来源归因
    implementation(libs.adjust.android)
    implementation(libs.android.installreferrer)
}
