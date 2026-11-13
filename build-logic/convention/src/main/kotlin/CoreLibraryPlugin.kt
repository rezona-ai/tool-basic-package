import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * core 下的公共模块：Android library + Kotlin，统一 SDK / Java 版本。
 * 有界面的模块在自己的 plugins 里再加 Compose 编译插件（alias(libs.plugins.kotlin.compose)），这里随之打开 compose。
 * 模块自己的 consumer-rules.pro（若有）随 AAR 带给应用模块的 R8。
 * Kotlin 的 jvmTarget 写在各模块的 build.gradle.kts（约定插件不依赖 Kotlin Gradle 插件的类）。
 */
class CoreLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")
        extensions.configure<LibraryExtension> {
            applyAndroidDefaults()
            if (file("consumer-rules.pro").isFile) defaultConfig.consumerProguardFiles("consumer-rules.pro")
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
            extensions.configure<LibraryExtension> { buildFeatures.compose = true }
        }
    }
}
