/**
 * [INPUT]: 无（纯 Kotlin，不依赖 Android）
 * [OUTPUT]: 对外提供 QrType、WifiSecurity、QrContent（各类型内容 + encode 成二维码文本）、QrContent.parse（识别结果 → 类型化内容）
 * [POS]: data 的二维码内容规则：生成时怎么拼文本、识别后怎么判断类型。格式按通行写法：WIFI:、vCard 3.0、MECARD:、mailto:、tel:
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.qrkit.data

enum class QrType(val label: String) {
    TEXT("Text"),
    LINK("Link"),
    WIFI("Wi-Fi"),
    CONTACT("Contact"),
    EMAIL("Email"),
    PHONE("Phone"),
}

enum class WifiSecurity(val label: String, val code: String) {
    WPA("WPA/WPA2/WPA3", "WPA"),
    WEP("WEP", "WEP"),
    NONE("None", "nopass"),
}

sealed class QrContent {
    abstract val type: QrType

    /** 写进二维码的文本 */
    abstract fun encode(): String

    /** 列表和结果页的标题 */
    abstract fun title(): String

    data class Text(val text: String) : QrContent() {
        override val type = QrType.TEXT
        override fun encode() = text
        override fun title() = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().ifEmpty { "Text" }
    }

    data class Link(val url: String) : QrContent() {
        override val type = QrType.LINK
        override fun encode() = normalizeUrl(url)
        override fun title() = encode().removePrefix("https://").removePrefix("http://").removeSuffix("/")
    }

    data class Wifi(val ssid: String, val password: String, val security: WifiSecurity, val hidden: Boolean = false) : QrContent() {
        override val type = QrType.WIFI
        override fun encode() = buildString {
            append("WIFI:T:").append(security.code).append(';')
            append("S:").append(escapeWifi(ssid)).append(';')
            if (security != WifiSecurity.NONE) append("P:").append(escapeWifi(password)).append(';')
            if (hidden) append("H:true;")
            append(';')
        }
        override fun title() = ssid.ifBlank { "Wi-Fi" }
    }

    data class Contact(val name: String, val phone: String = "", val email: String = "", val org: String = "") : QrContent() {
        override val type = QrType.CONTACT
        override fun encode() = buildString {
            append("BEGIN:VCARD\nVERSION:3.0\n")
            append("N:").append(escapeVcard(name)).append('\n')
            append("FN:").append(escapeVcard(name)).append('\n')
            if (org.isNotBlank()) append("ORG:").append(escapeVcard(org)).append('\n')
            if (phone.isNotBlank()) append("TEL:").append(escapeVcard(phone)).append('\n')
            if (email.isNotBlank()) append("EMAIL:").append(escapeVcard(email)).append('\n')
            append("END:VCARD")
        }
        override fun title() = name.ifBlank { phone.ifBlank { email.ifBlank { "Contact" } } }
    }

    data class Email(val address: String) : QrContent() {
        override val type = QrType.EMAIL
        override fun encode() = "mailto:$address"
        override fun title() = address
    }

    data class Phone(val number: String) : QrContent() {
        override val type = QrType.PHONE
        override fun encode() = "tel:$number"
        override fun title() = number
    }

    /** 生成前的校验：返回 null 表示可以生成，否则是给用户看的原因 */
    fun problem(): String? = when (this) {
        is Text -> if (text.isBlank()) "Enter some text" else null
        is Link -> if (!looksLikeUrl(url)) "Enter a web address, like example.com" else null
        is Wifi -> when {
            ssid.isBlank() -> "Enter the network name"
            security != WifiSecurity.NONE && password.isEmpty() -> "Enter the password, or choose None"
            else -> null
        }
        is Contact -> if (name.isBlank() && phone.isBlank() && email.isBlank()) "Enter a name, phone or email" else null
        is Email -> if (!EMAIL.matches(address.trim())) "Enter a valid email address" else null
        is Phone -> if (number.none { it.isDigit() }) "Enter a phone number" else null
    } ?: if (encode().length > MAX_LENGTH) "Too long for one QR code (max $MAX_LENGTH characters)" else null

    companion object {
        /** 一个二维码能放的字符上限（M 纠错级别下的保守值，留出余量保证容易识别） */
        const val MAX_LENGTH = 1200

        private val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
        private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

        fun normalizeUrl(raw: String): String {
            val url = raw.trim()
            return if (SCHEME.containsMatchIn(url)) url else "https://$url"
        }

        fun looksLikeUrl(raw: String): Boolean {
            val url = raw.trim()
            if (url.isEmpty() || url.any { it.isWhitespace() }) return false
            val host = normalizeUrl(url).substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
            return host.contains('.') && !host.startsWith('.') && !host.endsWith('.')
        }

        /** WIFI: 格式里 \ ; , : " 要加反斜杠 */
        fun escapeWifi(value: String) = value.replace(Regex("([\\\\;,:\"])"), "\\\\$1")

        /** vCard 3.0 里 \ ; , 和换行要转义 */
        fun escapeVcard(value: String) =
            value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

        /** 识别出的文本 → 类型化内容；认不出的都当作文本 */
        fun parse(raw: String): QrContent {
            val text = raw.trim()
            return when {
                text.startsWith("WIFI:", ignoreCase = true) -> parseWifi(text) ?: Text(raw)
                text.startsWith("BEGIN:VCARD", ignoreCase = true) -> parseVcard(text)
                text.startsWith("MECARD:", ignoreCase = true) -> parseMecard(text)
                text.startsWith("mailto:", ignoreCase = true) -> Email(text.substring(7).substringBefore('?'))
                text.startsWith("tel:", ignoreCase = true) -> Phone(text.substring(4))
                text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true) ->
                    if (text.none { it.isWhitespace() }) Link(text) else Text(raw)
                else -> Text(raw)
            }
        }

        /** 按未转义的分隔符切分，并去掉转义的反斜杠 */
        private fun splitEscaped(body: String, separator: Char): List<String> {
            val parts = mutableListOf<String>()
            val current = StringBuilder()
            var escaped = false
            for (c in body) {
                when {
                    escaped -> { current.append(c); escaped = false }
                    c == '\\' -> escaped = true
                    c == separator -> { parts += current.toString(); current.clear() }
                    else -> current.append(c)
                }
            }
            if (current.isNotEmpty()) parts += current.toString()
            return parts
        }

        private fun fields(body: String): Map<String, String> =
            splitEscaped(body, ';').mapNotNull { part ->
                val i = part.indexOf(':')
                if (i <= 0) null else part.substring(0, i).uppercase() to part.substring(i + 1)
            }.toMap()

        private fun parseWifi(text: String): Wifi? {
            val f = fields(text.substring(5))
            val ssid = f["S"] ?: return null
            val security = when (f["T"]?.uppercase()) {
                "WEP" -> WifiSecurity.WEP
                null, "", "NOPASS" -> if (f["P"].isNullOrEmpty()) WifiSecurity.NONE else WifiSecurity.WPA
                else -> WifiSecurity.WPA
            }
            return Wifi(ssid, f["P"].orEmpty(), security, f["H"].equals("true", ignoreCase = true))
        }

        private fun unescapeVcard(value: String) = buildString {
            var escaped = false
            for (c in value) {
                when {
                    escaped -> { append(if (c == 'n' || c == 'N') '\n' else c); escaped = false }
                    c == '\\' -> escaped = true
                    else -> append(c)
                }
            }
        }

        private fun parseVcard(text: String): Contact {
            var name = ""
            var structured = ""
            var phone = ""
            var email = ""
            var org = ""
            text.lines().forEach { line ->
                val i = line.indexOf(':')
                if (i <= 0) return@forEach
                val key = line.substring(0, i).substringBefore(';').uppercase()
                val value = line.substring(i + 1).trim()
                when (key) {
                    "FN" -> name = unescapeVcard(value)
                    "N" -> structured = splitEscaped(value, ';').filter { it.isNotBlank() }.reversed().joinToString(" ")
                    "TEL" -> if (phone.isEmpty()) phone = unescapeVcard(value)
                    "EMAIL" -> if (email.isEmpty()) email = unescapeVcard(value)
                    // ORG 里未转义的 ; 分隔部门层级，转义的 \; 是名称本身的分号
                    "ORG" -> org = splitEscaped(value, ';').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
                }
            }
            return Contact(name.ifBlank { structured }, phone, email, org)
        }

        private fun parseMecard(text: String): Contact {
            val f = fields(text.substring(7))
            val name = f["N"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.reversed().joinToString(" ")
            return Contact(name, f["TEL"].orEmpty(), f["EMAIL"].orEmpty(), f["ORG"].orEmpty())
        }
    }
}
