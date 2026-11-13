import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.io.File
import java.util.Properties

/**
 * 品牌应用模块（apps/<品牌>）：Android application + Kotlin + Compose，并从模块目录下的 brand.properties 注入
 * 包名、版本、应用名、链接、广告 ID、签名。
 *
 * 取值优先级（高 → 低）：
 * 1. 模块目录下的 brand.local.properties：不入库，只对本应用生效。正式的 AdMob / AppLovin / Adjust ID 放这里，
 *    多个品牌应用各放各的，不会互相串。文件不存在或某项留空时跳过。
 * 2. 同名 Gradle 属性（-P 或 ~/.gradle/gradle.properties）：对所有品牌应用生效，只适合放本机通用的设置，例如 ADMOB_TEST_DEVICE_IDS。
 * 3. brand.properties：入库的默认值（广告位是 Google 测试 ID）。
 *
 * release 构建前执行 validateBrand：包名还是示例、隐私网址、网站地址或反馈邮箱无效时直接失败。
 * 打上传用的 AAB（bundleRelease）前再执行 validateUpload：AdMob 还是 Google 测试 ID 时失败（release APK 只做本机测试，不拦）。
 * debug 包固定使用 Google 测试广告位，不读 brand.properties 里的广告位。
 */
class BrandAppPlugin : Plugin<Project> {

    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        val brandFile = file("brand.properties")
        if (!brandFile.isFile) throw GradleException("$path: brand.properties not found in ${projectDir.relativeTo(rootDir)}")
        val brandProps = Properties().apply { brandFile.reader(Charsets.UTF_8).use { load(it) } }
        val label = "${projectDir.relativeTo(rootDir)}/brand.properties"
        val localFile = file("brand.local.properties")
        val localProps = Properties().apply { if (localFile.isFile) localFile.reader(Charsets.UTF_8).use { load(it) } }

        fun localProp(key: String): String? = localProps.getProperty(key)?.takeIf { it.isNotBlank() }
        fun prop(key: String): String = localProp(key) ?: providers.gradleProperty(key).orNull ?: brandProps.getProperty(key, "")
        fun required(key: String): String = prop(key).trim().ifEmpty { throw GradleException("$label: $key is required") }

        val appId = required("APPLICATION_ID")
        val versionCode = required("VERSION_CODE").toIntOrNull() ?: throw GradleException("$label: VERSION_CODE must be an integer")
        val versionName = required("VERSION_NAME")
        val appName = required("APP_NAME")

        // 上传密钥：SIGNING_PROPERTIES 指定项目根目录的密钥配置文件（配置文件和密钥库都不入库）。
        // 未填或文件不存在时 release 用 debug 签名，只能自测、不能上传 Play
        val signingPropsName = prop("SIGNING_PROPERTIES").trim()
        val keystoreProps = Properties().apply {
            if (signingPropsName.isNotEmpty()) rootProject.file(signingPropsName).takeIf { it.isFile }?.inputStream()?.use { load(it) }
        }
        val hasUploadKey = keystoreProps.getProperty("storeFile")?.let { rootProject.file(it).isFile } == true

        extensions.configure<ApplicationExtension> {
            applyAndroidDefaults()
            buildFeatures.buildConfig = true
            buildFeatures.compose = true

            defaultConfig {
                applicationId = appId
                targetSdk = AndroidDefaults.TARGET_SDK
                this.versionCode = versionCode
                this.versionName = versionName
                resValue("string", "app_name", appName)

                buildConfigField("String", "APP_NAME", javaString(appName))
                buildConfigField("String", "APP_TAGLINE", javaString(prop("APP_TAGLINE")))
                buildConfigField("String", "PRIVACY_URL", javaString(prop("PRIVACY_URL")))
                buildConfigField("String", "FEEDBACK_EMAIL", javaString(prop("FEEDBACK_EMAIL")))
                listOf("APPLOVIN_SDK_KEY", "MAX_BANNER_AD_UNIT_ID", "MAX_INTERSTITIAL_AD_UNIT_ID", "MAX_APP_OPEN_AD_UNIT_ID").forEach { key ->
                    buildConfigField("String", key, javaString(prop(key)))
                }
                // AdMob App ID 写入清单（MAX 接入 Google Adapter 时同样需要）
                manifestPlaceholders["admobAppId"] = prop("ADMOB_APP_ID")
                buildConfigField("String", "ADMOB_TEST_DEVICE_IDS", javaString(prop("ADMOB_TEST_DEVICE_IDS")))
                // Adjust 归因：留空不初始化；debug 包走 Sandbox，release 包走 Production
                buildConfigField("String", "ADJUST_APP_TOKEN", javaString(prop("ADJUST_APP_TOKEN")))
            }

            if (hasUploadKey) {
                signingConfigs.create("upload") {
                    storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                    storePassword = keystoreProps.getProperty("storePassword")
                    keyAlias = keystoreProps.getProperty("keyAlias")
                    keyPassword = keystoreProps.getProperty("keyPassword")
                }
            }

            buildTypes {
                getByName("debug") {
                    // debug 包固定使用 Google 官方测试广告位，开发和自测时不会产生无效流量
                    // https://developers.google.com/admob/android/test-ads
                    buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", javaString(TEST_BANNER))
                    buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", javaString(TEST_INTERSTITIAL))
                    buildConfigField("String", "ADMOB_APP_OPEN_AD_UNIT_ID", javaString(TEST_APP_OPEN))
                }
                getByName("release") {
                    listOf("ADMOB_BANNER_AD_UNIT_ID", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "ADMOB_APP_OPEN_AD_UNIT_ID").forEach { key ->
                        buildConfigField("String", key, javaString(prop(key)))
                    }
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                    signingConfig = if (hasUploadKey) {
                        signingConfigs.getByName("upload")
                    } else {
                        logger.warn("$path: ${signingPropsName.ifEmpty { "SIGNING_PROPERTIES" }} not set or not found: release is signed with the debug key and cannot be uploaded to Google Play")
                        signingConfigs.getByName("debug")
                    }
                }
            }
        }

        /** release 包上架前的品牌校验：缺失或仍是示例值时直接失败 */
        val values = listOf("APPLICATION_ID", "PRIVACY_URL", "WEBSITE_URL", "FEEDBACK_EMAIL").associateWith { prop(it).trim() }
        val validateBrand = tasks.register("validateBrand") {
            group = "verification"
            description = "Checks $label before building a release"
            doLast {
                val problems = buildList {
                    val id = values.getValue("APPLICATION_ID")
                    if (id.startsWith("com.example") || !PACKAGE_NAME.matches(id)) add("APPLICATION_ID is not a valid package name: $id")
                    val url = values.getValue("PRIVACY_URL")
                    if (!url.startsWith("https://") || "example." in url) add("PRIVACY_URL must be a real https:// address: $url")
                    val site = values.getValue("WEBSITE_URL")
                    if (!site.startsWith("https://") || "example." in site) add("WEBSITE_URL must be a real https:// address (app-ads.txt is served from it): $site")
                    val email = values.getValue("FEEDBACK_EMAIL")
                    if (!EMAIL.matches(email) || email.endsWith("example.com")) add("FEEDBACK_EMAIL must be a real address: $email")
                }
                if (problems.isNotEmpty()) throw GradleException("$label:\n- " + problems.joinToString("\n- "))
            }
        }
        tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validateBrand) }

        /** 上传 Play 的 AAB：不能带 Google 测试广告 ID（基座默认就是测试 ID，所以基座打不出可上传的 AAB） */
        val adKeys = listOf("ADMOB_APP_ID", "ADMOB_BANNER_AD_UNIT_ID", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "ADMOB_APP_OPEN_AD_UNIT_ID")
        val adIds = adKeys.associateWith { prop(it).trim() }
        // 广告 ID 来自 Gradle 属性时，所有品牌应用都会读到同一套，只提醒、不拦
        val sharedAdKeys = adKeys.filter { localProp(it) == null && providers.gradleProperty(it).isPresent }
        val localLabel = "${projectDir.relativeTo(rootDir)}/brand.local.properties"
        val validateUpload = tasks.register("validateUpload") {
            group = "verification"
            description = "Checks $label has real AdMob IDs before building an uploadable bundle"
            dependsOn(validateBrand)
            doLast {
                if (sharedAdKeys.isNotEmpty()) {
                    logger.warn("$path: ${sharedAdKeys.joinToString()} come from Gradle properties, which every brand app reads. Put this app's IDs in $localLabel instead.")
                }
                val problems = adIds.filter { (_, v) -> v.isEmpty() || TEST_PUBLISHER in v }.keys
                if (problems.isNotEmpty()) {
                    throw GradleException(
                        "$label: ${problems.joinToString()} still use Google test ads (or are empty). " +
                            "Put this app's own AdMob IDs in $localLabel (not committed) before building an AAB for Google Play.",
                    )
                }
            }
        }
        tasks.matching { it.name == "bundleRelease" }.configureEach { dependsOn(validateUpload) }
    }

    private companion object {
        /** Google 官方测试广告的发布商 ID */
        const val TEST_PUBLISHER = "ca-app-pub-3940256099942544"
        const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
        const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
        const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
        val PACKAGE_NAME = Regex("[a-zA-Z][\\w]*(\\.[a-zA-Z][\\w]*)+")
        val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")

        /** 写进 BuildConfig 的字符串字面量 */
        fun javaString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
