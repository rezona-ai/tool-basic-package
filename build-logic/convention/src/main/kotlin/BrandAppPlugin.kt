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
 * 4. 账号默认值（可选）：brand.properties 填了 ACCOUNT 时读 accounts/<ACCOUNT>/account.properties（不入库），
 *    SIGNING_PROPERTIES、FEEDBACK_EMAIL 在应用没填时用账号的；打 AAB 前另做账号串号校验（AccountGuard）。
 *    没填 ACCOUNT 的应用不受影响。
 *
 * release 构建前执行 validateBrand：包名还是示例、隐私网址、网站地址或反馈邮箱无效时直接失败。
 * 打上传用的 AAB（bundleRelease）前再执行 validateUpload：AdMob 还是 Google 测试 ID 时失败（release APK 只做本机测试，不拦）。
 * debug 包固定使用 Google 测试广告位，不读 brand.properties 里的广告位。
 *
 * 广告开关（可选，只读 brand.local.properties / brand.properties 的 ADS）：
 * - 不填：和以前完全一样（不写 BuildConfig.ADS_ENABLED），已上架的包构建产物不变。
 * - ADS=off：无广告包（白包）。广告、授权弹窗、归因 SDK（AD_SDKS）不进 APK / AAB，清单里的 AdMob App ID 留空，
 *   validateUpload 和 AccountGuard 不再要求 AdMob ID、发布商和 app-ads.txt。应用代码按 BuildConfig.ADS_ENABLED 不初始化广告和归因。
 * - ADS=on：正常带广告，同时写 BuildConfig.ADS_ENABLED = true（白包上架后发带广告的版本时改成 on）。
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

        // 账号（可选）：ACCOUNT 指向 accounts/<ACCOUNT>/account.properties（不入库），见 AccountGuard。
        // 没填 ACCOUNT 时 accountProps 为空，取值和以前完全一样
        val account = (localProp("ACCOUNT") ?: brandProps.getProperty("ACCOUNT", "")).trim()
        val accountProps = if (account.isEmpty()) Properties() else AccountGuard.load(AccountGuard.accountFile(rootDir, account))
        if (account.isNotEmpty() && accountProps.isEmpty) {
            logger.warn("$path: ACCOUNT=$account but ${AccountGuard.ACCOUNTS_DIR}/$account/account.properties is missing; debug builds still work, uploads are blocked")
        }
        fun accountProp(key: String): String? = accountProps.getProperty(key)?.takeIf { it.isNotBlank() }
        // 优先级：brand.local.properties > Gradle 属性 > brand.properties（非空）> 账号配置的默认值（只有 ACCOUNT_DEFAULTS 里的键）
        fun prop(key: String): String = localProp(key)
            ?: providers.gradleProperty(key).orNull
            ?: brandProps.getProperty(key)?.takeIf { it.isNotBlank() }
            ?: (if (key in ACCOUNT_DEFAULTS) accountProp(key) else null)
            ?: brandProps.getProperty(key, "")
        fun required(key: String): String = prop(key).trim().ifEmpty { throw GradleException("$label: $key is required") }

        val appId = required("APPLICATION_ID")
        val versionCode = required("VERSION_CODE").toIntOrNull() ?: throw GradleException("$label: VERSION_CODE must be an integer")
        val versionName = required("VERSION_NAME")
        val appName = required("APP_NAME")

        // 广告开关：只读本应用的文件（不读 Gradle 属性，免得一个全局属性改到所有应用）；不填时行为和以前完全一样
        val adsSetting = (localProp("ADS") ?: brandProps.getProperty("ADS", "")).trim().lowercase()
        if (adsSetting !in setOf("", "on", "off")) throw GradleException("$label: ADS must be on or off: $adsSetting")
        val adsOff = adsSetting == "off"
        if (adsOff) {
            // 白包：广告、授权弹窗、归因 SDK 不进运行时（编译期仍可见，core:ads 照常编译；应用代码按 ADS_ENABLED 不去用它们）
            configurations.matching { it.name.endsWith("RuntimeClasspath") }.configureEach {
                AD_SDKS.forEach { (group, module) -> exclude(mapOf("group" to group, "module" to module)) }
            }
        }

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
                // AdMob App ID 写入清单（MAX 接入 Google Adapter 时同样需要）；白包留空
                manifestPlaceholders["admobAppId"] = if (adsOff) "" else prop("ADMOB_APP_ID")
                if (adsSetting.isNotEmpty()) buildConfigField("boolean", "ADS_ENABLED", (!adsOff).toString())
                buildConfigField("String", "ADMOB_TEST_DEVICE_IDS", javaString(prop("ADMOB_TEST_DEVICE_IDS")))
                // Adjust 归因：留空不初始化；debug 包走 Sandbox，release 包走 Production
                buildConfigField("String", "ADJUST_APP_TOKEN", javaString(prop("ADJUST_APP_TOKEN")))
            }

            if (adsOff) {
                // 白包：用构建类型的清单（优先级高于 src/main）去掉 AdMob App ID 和广告 ID 权限，清单里不留广告痕迹。
                // 应用自己有 src/<debug|release>/AndroidManifest.xml 时不覆盖它
                val overlay = layout.buildDirectory.file("appbase/no-ads/AndroidManifest.xml").get().asFile
                overlay.parentFile.mkdirs()
                overlay.writeText(NO_ADS_MANIFEST)
                listOf("debug", "release").forEach { type ->
                    if (file("src/$type/AndroidManifest.xml").isFile) {
                        logger.warn("$path: src/$type/AndroidManifest.xml exists, ADS=off does not strip the AdMob App ID from it")
                    } else {
                        sourceSets.getByName(type).manifest.srcFile(overlay)
                    }
                }
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
                    if (adsOff) {
                        // core:ads 里引用了被排除的 SDK 类，R8 默认把缺类当错误
                        val rules = layout.buildDirectory.file("appbase/no-ads-rules.pro").get().asFile
                        rules.parentFile.mkdirs()
                        rules.writeText(AD_SDK_PACKAGES.joinToString("\n", postfix = "\n") { "-dontwarn $it.**" })
                        proguardFile(rules)
                    }
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
        // 填了 ACCOUNT 的应用再做账号串号校验（AccountGuard）；只捕获文件路径和取值，不捕获 Project
        val accountCheck: (() -> List<String>)? = if (account.isEmpty()) null else {
            val root = rootDir
            val dir = projectDir
            val resolved = (listOf("APPLICATION_ID", "PRIVACY_URL", "WEBSITE_URL", "FEEDBACK_EMAIL") + adKeys).associateWith { prop(it).trim() }
            val shared = sharedAdKeys.toList()
            val upload = hasUploadKey
            val ads = !adsOff
            ({ AccountGuard.check(root, dir, account, resolved, shared, upload, ads) })
        }
        val validateUpload = tasks.register("validateUpload") {
            group = "verification"
            description = "Checks $label has real AdMob IDs before building an uploadable bundle"
            dependsOn(validateBrand)
            doLast {
                if (sharedAdKeys.isNotEmpty()) {
                    logger.warn("$path: ${sharedAdKeys.joinToString()} come from Gradle properties, which every brand app reads. Put this app's IDs in $localLabel instead.")
                }
                // 白包不带广告，不要求 AdMob ID
                val problems = if (adsOff) emptySet() else adIds.filter { (_, v) -> v.isEmpty() || TEST_PUBLISHER in v }.keys
                if (problems.isNotEmpty()) {
                    throw GradleException(
                        "$label: ${problems.joinToString()} still use Google test ads (or are empty). " +
                            "Put this app's own AdMob IDs in $localLabel (not committed) before building an AAB for Google Play.",
                    )
                }
                if (accountCheck != null) {
                    val accountProblems = accountCheck()
                    if (accountProblems.isNotEmpty()) {
                        throw GradleException("$label (ACCOUNT=$account):\n- " + accountProblems.joinToString("\n- "))
                    }
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

        /** 账号配置能提供默认值的键（应用自己在 brand.properties / brand.local.properties 填了就以应用为准） */
        val ACCOUNT_DEFAULTS = setOf("SIGNING_PROPERTIES", "FEEDBACK_EMAIL")

        /** ADS=off 时不进运行时的 SDK（group, module）：AdMob、UMP 授权弹窗、AppLovin MAX、Adjust、Install Referrer，与 core:ads 的依赖一一对应 */
        val AD_SDKS = listOf(
            "com.google.android.gms" to "play-services-ads",
            "com.google.android.ump" to "user-messaging-platform",
            "com.applovin" to "applovin-sdk",
            "com.adjust.sdk" to "adjust-android",
            "com.android.installreferrer" to "installreferrer",
        )

        /** ADS=off 时叠加的清单：去掉 AdMob App ID 和广告 ID 相关权限（SDK 已排除，这里兜底） */
        val NO_ADS_MANIFEST = """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- appbase.brand-app 生成：ADS=off（白包）时去掉广告痕迹，不要手改 -->
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools">
                <uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove" />
                <uses-permission android:name="android.permission.ACCESS_ADSERVICES_AD_ID" tools:node="remove" />
                <uses-permission android:name="android.permission.ACCESS_ADSERVICES_ATTRIBUTION" tools:node="remove" />
                <uses-permission android:name="android.permission.ACCESS_ADSERVICES_TOPICS" tools:node="remove" />
                <application>
                    <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" tools:node="remove" />
                </application>
            </manifest>
        """.trimIndent() + "\n"

        /** 上面这些 SDK 的 Java 包名（R8 -dontwarn） */
        val AD_SDK_PACKAGES = listOf(
            "com.google.android.gms.ads",
            "com.google.android.ump",
            "com.applovin",
            "com.adjust.sdk",
            "com.android.installreferrer",
        )

        /** 写进 BuildConfig 的字符串字面量 */
        fun javaString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
