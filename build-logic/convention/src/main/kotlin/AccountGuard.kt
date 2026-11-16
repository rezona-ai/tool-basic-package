import java.io.File
import java.net.URI
import java.util.Properties

/**
 * 账号维度的串号校验：brand.properties 填了 ACCOUNT 的品牌应用，打上传用的 AAB 前检查它只带本账号的东西。
 *
 * 账号配置在 accounts/<ACCOUNT>/account.properties（不入库），字段：
 * - DEVELOPER_NAME：Play 管理中心里的开发者名称
 * - WEBSITE_ROOT：本账号的开发者网站根地址（app-ads.txt 部署在这里），本账号各包的 PRIVACY_URL / WEBSITE_URL 都在这个域名下
 * - FEEDBACK_EMAIL：本账号的联系邮箱，作为本账号各包的默认反馈邮箱
 * - ADMOB_PUBLISHER_ID：本账号对应的 AdMob 发布商 ID（pub-16 位数字）
 * - SIGNING_PROPERTIES：本账号的上传密钥配置文件（项目根目录，不入库），作为本账号各包的默认签名
 *
 * 没填 ACCOUNT 的应用（例如 Orbit Clean）完全不走这里，构建结果不变。
 * 只检查“本账号的值有没有填对、有没有混进别的账号或别的包的值”，不做任何隐藏代码来源的处理。
 */
object AccountGuard {

    const val ACCOUNTS_DIR = "accounts"
    val ACCOUNT_KEYS = listOf("DEVELOPER_NAME", "WEBSITE_ROOT", "FEEDBACK_EMAIL", "ADMOB_PUBLISHER_ID", "SIGNING_PROPERTIES")
    private val PUBLISHER = Regex("pub-(\\d{16})")
    private val AD_ID_PUBLISHER = Regex("ca-app-pub-(\\d{16})[~/]\\d+")
    private const val TEST_PUBLISHER_DIGITS = "3940256099942544"

    fun accountFile(rootDir: File, account: String) = File(rootDir, "$ACCOUNTS_DIR/$account/account.properties")

    fun load(file: File): Properties = Properties().apply { if (file.isFile) file.reader(Charsets.UTF_8).use { load(it) } }

    /** 一个应用（或账号）对外可见的身份值，用来判断有没有串到别的账号 */
    private class Identity(val label: String, val values: Set<String>)

    /**
     * @param values 本应用最终生效的取值（已按 brand.local > 账号 > Gradle 属性 > brand.properties 合并）
     * @param sharedAdKeys 来自 Gradle 属性（所有应用共用）的广告 ID 键
     * @return 问题列表，空表示通过
     */
    fun check(
        rootDir: File,
        projectDir: File,
        account: String,
        values: Map<String, String>,
        sharedAdKeys: List<String>,
        hasUploadKey: Boolean,
    ): List<String> = buildList {
        val accountFile = accountFile(rootDir, account)
        if (!accountFile.isFile) {
            add("ACCOUNT=$account but ${accountFile.relativeTo(rootDir)} does not exist (copy accounts/example/account.properties.example)")
            return@buildList
        }
        val acc = load(accountFile)
        val missing = ACCOUNT_KEYS.filter { acc.getProperty(it).isNullOrBlank() }
        if (missing.isNotEmpty()) add("${accountFile.relativeTo(rootDir)}: ${missing.joinToString()} must be filled")

        // 1. 广告 ID 必须来自本应用自己的配置，并且都属于本账号的 AdMob 发布商
        if (sharedAdKeys.isNotEmpty()) {
            add("${sharedAdKeys.joinToString()} come from Gradle properties (shared by every app). Put this app's IDs in brand.local.properties")
        }
        val publisher = acc.getProperty("ADMOB_PUBLISHER_ID", "").trim()
        val pubDigits = PUBLISHER.matchEntire(publisher)?.groupValues?.get(1)
        if (pubDigits == null) {
            add("ADMOB_PUBLISHER_ID must look like pub-0000000000000000: $publisher")
        } else {
            AD_KEYS.forEach { key ->
                val id = values[key].orEmpty()
                val digits = AD_ID_PUBLISHER.matchEntire(id)?.groupValues?.get(1)
                if (digits != null && digits != TEST_PUBLISHER_DIGITS && digits != pubDigits) {
                    add("$key belongs to publisher pub-$digits, not this account's $publisher")
                }
            }
        }

        // 2. 上传密钥
        if (!hasUploadKey) add("SIGNING_PROPERTIES (from brand.properties or the account) is missing or its storeFile does not exist")

        // 3. 隐私政策和网站在本账号的网站域名下
        val rootHost = host(acc.getProperty("WEBSITE_ROOT", ""))
        if (rootHost == null) {
            add("WEBSITE_ROOT must be a https:// address")
        } else {
            listOf("PRIVACY_URL", "WEBSITE_URL").forEach { key ->
                val h = host(values[key].orEmpty())
                if (h != rootHost) add("$key must be on this account's site $rootHost: ${values[key]}")
            }
        }

        // 4. 网站根目录的 app-ads.txt 只写本账号的发布商
        val appAds = File(projectDir, "store/app-ads.txt")
        if (pubDigits != null && appAds.isFile) {
            val pubs = AD_TXT_PUBLISHER.findAll(appAds.readText()).map { it.groupValues[1] }.toSet()
            if (pubs != setOf(pubDigits)) {
                add("store/app-ads.txt must list only $publisher (found ${pubs.joinToString { "pub-$it" }.ifEmpty { "none" }})")
            }
        }

        // 5. 本应用的取值和源码里不能出现别的账号（以及没填账号的应用）的身份值
        val foreign = foreignIdentities(rootDir, projectDir, account)
        val own = listOf("APPLICATION_ID", "PRIVACY_URL", "WEBSITE_URL", "FEEDBACK_EMAIL") + AD_KEYS
        foreign.forEach { id ->
            own.forEach { key ->
                val v = values[key].orEmpty().lowercase()
                id.values.firstOrNull { it.isNotEmpty() && (v == it || (it.length > 6 && it in v)) }
                    ?.let { add("$key contains \"$it\" from ${id.label}") }
            }
        }
        val sources = listOf(File(projectDir, "src/main"), File(projectDir, "store")).flatMap { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension in TEXT_EXTS }.toList()
        }
        for (file in sources) {
            val text = file.readText().lowercase()
            for (id in foreign) {
                id.values.firstOrNull { it.length > 3 && it in text }
                    ?.let { add("${file.relativeTo(projectDir)} contains \"$it\" from ${id.label}") }
            }
        }
    }

    /** 别的账号、以及所有不属于本账号的应用的身份值（小写） */
    private fun foreignIdentities(rootDir: File, projectDir: File, account: String): List<Identity> {
        val out = ArrayList<Identity>()
        File(rootDir, ACCOUNTS_DIR).listFiles()?.filter { it.isDirectory && it.name != account }?.forEach { dir ->
            val p = load(File(dir, "account.properties"))
            if (p.isEmpty) return@forEach
            out += Identity(
                "account ${dir.name}",
                setOfNotNull(
                    p.getProperty("DEVELOPER_NAME"),
                    host(p.getProperty("WEBSITE_ROOT", "")),
                    p.getProperty("FEEDBACK_EMAIL"),
                    PUBLISHER.matchEntire(p.getProperty("ADMOB_PUBLISHER_ID", "").trim())?.groupValues?.get(1),
                ).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet(),
            )
        }
        File(rootDir, "apps").listFiles()?.filter { it.isDirectory && it.canonicalFile != projectDir.canonicalFile }?.forEach { dir ->
            val brand = load(File(dir, "brand.properties"))
            if (brand.isEmpty) return@forEach
            val local = load(File(dir, "brand.local.properties"))
            fun v(key: String) = local.getProperty(key)?.takeIf { it.isNotBlank() } ?: brand.getProperty(key, "")
            if (v("ACCOUNT").trim() == account) return@forEach
            val values = setOfNotNull(
                v("APPLICATION_ID").takeUnless { it.startsWith("com.example") || it.startsWith("com.verify") },
                v("APP_NAME"),
                host(v("PRIVACY_URL")),
                host(v("WEBSITE_URL")),
                v("FEEDBACK_EMAIL").takeUnless { it.endsWith("example.com") },
                AD_ID_PUBLISHER.matchEntire(v("ADMOB_APP_ID").trim())?.groupValues?.get(1)?.takeUnless { it == TEST_PUBLISHER_DIGITS },
            ).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            out += Identity("apps/${dir.name}", values)
        }
        return out
    }

    /** https 地址的主机名；example.* 占位和非 https 返回 null */
    private fun host(url: String): String? = runCatching { URI(url.trim()) }.getOrNull()
        ?.takeIf { it.scheme == "https" }?.host?.lowercase()?.takeUnless { it.startsWith("example.") || ".example." in it }

    private val AD_KEYS = listOf("ADMOB_APP_ID", "ADMOB_BANNER_AD_UNIT_ID", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "ADMOB_APP_OPEN_AD_UNIT_ID")
    private val AD_TXT_PUBLISHER = Regex("pub-(\\d{16})")
    private val TEXT_EXTS = setOf("kt", "xml", "html", "txt", "md", "json")
}
