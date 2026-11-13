// 构建约定（included build）：core 模块和各品牌应用模块共用的 Gradle 配置，见 convention/src/main/kotlin
pluginManagement {
    // kotlin-dsl 依赖的 Kotlin 构件在 Maven Central 上也有，放在插件门户前面
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
