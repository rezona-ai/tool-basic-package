import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion

/** 所有模块统一的 SDK 与 Java 版本 */
internal object AndroidDefaults {
    const val COMPILE_SDK = 36
    const val MIN_SDK = 26
    const val TARGET_SDK = 36
    val JAVA = JavaVersion.VERSION_17
}

internal fun CommonExtension<*, *, *, *, *, *>.applyAndroidDefaults() {
    compileSdk = AndroidDefaults.COMPILE_SDK
    defaultConfig.minSdk = AndroidDefaults.MIN_SDK
    compileOptions {
        sourceCompatibility = AndroidDefaults.JAVA
        targetCompatibility = AndroidDefaults.JAVA
    }
}
