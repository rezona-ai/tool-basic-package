plugins {
    `kotlin-dsl`
}

dependencies {
    // 只在编译约定插件时需要 AGP 的 API；运行时由根项目的 plugins { ... apply false } 提供
    compileOnly(libs.android.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("coreLibrary") {
            id = "appbase.core-library"
            implementationClass = "CoreLibraryPlugin"
        }
        register("brandApp") {
            id = "appbase.brand-app"
            implementationClass = "BrandAppPlugin"
        }
    }
}
