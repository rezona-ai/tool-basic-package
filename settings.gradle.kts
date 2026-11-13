pluginManagement {
    // 构建约定：appbase.core-library（core 模块）、appbase.brand-app（apps 下的品牌应用）
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AppBase"

// 公共模块：所有品牌共用，不含任何品牌值
include(":core:common", ":core:ads", ":core:storage", ":core:ui")

// 品牌应用：apps 下每个含 build.gradle.kts 的目录是一个应用模块（模块路径 :apps:<目录名>）
rootDir.resolve("apps").listFiles()
    ?.filter { it.isDirectory && it.resolve("build.gradle.kts").isFile }
    ?.sortedBy { it.name }
    ?.forEach { include(":apps:${it.name}") }
