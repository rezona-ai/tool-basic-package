/**
 * [INPUT]: 无（纯 Kotlin + java.time，不依赖 Android）
 * [OUTPUT]: 对外提供 RuleType、RenameRule（各规则的参数）、SourceFile、PlanItem、RenamePlanner（规则 → 新文件名 + 问题检查）、FileNames（拆扩展名、非法名检查）
 * [POS]: data 的改名规则真相源，有单测。扩展名一律保留不变，规则只作用于文件名主体；
 *        问题检查覆盖：空名、非法字符、过长、同一批里重名、和文件夹里没选中的文件重名（都按不区分大小写比较，和手机存储一致）
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.renamer.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class RuleType(val label: String, val summary: String, val example: String) {
    NUMBER("Number in order", "Base name plus a running number", "IMG_2041.jpg → Trip_001.jpg"),
    DATE("Add date", "Date the file was last changed", "scan.pdf → 2026-03-14_scan.pdf"),
    AFFIX("Prefix and suffix", "Add text before or after the name", "report.docx → Q3_report_final.docx"),
    REPLACE("Find and replace", "Swap one piece of text for another", "IMG 0041.jpg → Paris 0041.jpg"),
    CASE("Change case", "lowercase, UPPERCASE or Title Case", "My Notes.txt → my notes.txt"),
}

enum class SortOrder(val label: String) { NAME("Name"), MODIFIED("Date") }

enum class CaseMode(val label: String) { LOWER("lowercase"), UPPER("UPPERCASE"), TITLE("Title Case") }

enum class DateFormat(val label: String, val pattern: String) {
    DASHED("2026-03-14", "yyyy-MM-dd"),
    COMPACT("20260314", "yyyyMMdd"),
    WITH_TIME("2026-03-14_0930", "yyyy-MM-dd_HHmm"),
}

/** 日期规则里日期后面接什么 */
enum class DateThen(val label: String) { ORIGINAL("Original name"), NUMBER("Number") }

data class RenameRule(
    val type: RuleType,
    val baseName: String = "File",
    val start: Int = 1,
    val digits: Int = 3,
    val separator: String = "_",
    val dateFormat: DateFormat = DateFormat.DASHED,
    val dateThen: DateThen = DateThen.ORIGINAL,
    val prefix: String = "",
    val suffix: String = "",
    val find: String = "",
    val replace: String = "",
    val ignoreCase: Boolean = true,
    val caseMode: CaseMode = CaseMode.LOWER,
)

data class SourceFile(val name: String, val modified: Long)

data class PlanItem(val oldName: String, val newName: String, val problem: String?) {
    val changed: Boolean get() = oldName != newName
}

object FileNames {
    /** 文件系统不允许的字符（FAT / exFAT 也要兼容：SD 卡、U 盘） */
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")
    const val MAX_BYTES = 255

    /** 拆成主体和扩展名（含点）；以点开头且只有一个点的（.nomedia）、没有点的都视为没有扩展名 */
    fun split(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.length - 1) name to "" else name.substring(0, dot) to name.substring(dot)
    }

    fun problem(name: String): String? = when {
        name.isBlank() -> "Name can't be empty"
        name != name.trim() -> "Name can't start or end with a space"
        name.startsWith(".") -> "Name can't start with a dot"
        name.endsWith(".") -> "Name can't end with a dot"
        ILLEGAL.containsMatchIn(name) -> "Can't contain \\ / : * ? \" < > |"
        name.toByteArray(Charsets.UTF_8).size > MAX_BYTES -> "Name is too long"
        else -> null
    }
}

object RenamePlanner {

    /** 按排序方式排好的顺序（编号规则按这个顺序编号，预览也按这个顺序显示） */
    fun sorted(files: List<SourceFile>, order: SortOrder): List<SourceFile> = when (order) {
        SortOrder.NAME -> files.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        SortOrder.MODIFIED -> files.sortedWith(compareBy<SourceFile> { it.modified }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** 对已排好序的文件计算新文件名（和输入一一对应） */
    fun newNames(files: List<SourceFile>, rule: RenameRule, zone: ZoneId = ZoneId.systemDefault()): List<String> {
        val dateFormatter = DateTimeFormatter.ofPattern(rule.dateFormat.pattern, Locale.US).withZone(zone)
        val perDate = HashMap<String, Int>()
        return files.mapIndexed { index, file ->
            val (base, ext) = FileNames.split(file.name)
            val newBase = when (rule.type) {
                RuleType.NUMBER -> rule.baseName.trim() + rule.separator + pad(rule.start + index, rule.digits)
                RuleType.DATE -> {
                    val date = dateFormatter.format(Instant.ofEpochMilli(file.modified))
                    when (rule.dateThen) {
                        DateThen.ORIGINAL -> date + rule.separator + base
                        DateThen.NUMBER -> {
                            val n = perDate.merge(date, 1, Int::plus)!!
                            date + rule.separator + pad(rule.start + n - 1, rule.digits)
                        }
                    }
                }
                RuleType.AFFIX -> rule.prefix + base + rule.suffix
                RuleType.REPLACE -> if (rule.find.isEmpty()) base else base.replace(rule.find, rule.replace, ignoreCase = rule.ignoreCase)
                RuleType.CASE -> when (rule.caseMode) {
                    CaseMode.LOWER -> base.lowercase(Locale.ROOT)
                    CaseMode.UPPER -> base.uppercase(Locale.ROOT)
                    CaseMode.TITLE -> titleCase(base)
                }
            }
            newBase + ext
        }
    }

    /**
     * 生成预览：[selected] 是要改名的文件（已排序），[others] 是同一文件夹里没选中的文件名。
     * 只有名字变了的才检查问题；没变的直接跳过。
     */
    fun plan(selected: List<SourceFile>, rule: RenameRule, others: Collection<String>, zone: ZoneId = ZoneId.systemDefault()): List<PlanItem> {
        val names = newNames(selected, rule, zone)
        val counts = names.groupingBy { it.lowercase(Locale.ROOT) }.eachCount()
        val taken = others.map { it.lowercase(Locale.ROOT) }.toSet()
        return selected.zip(names) { file, newName ->
            val problem = if (newName == file.name) null else FileNames.problem(newName) ?: when {
                (counts[newName.lowercase(Locale.ROOT)] ?: 0) > 1 -> "Same name as another file in this batch"
                newName.lowercase(Locale.ROOT) in taken -> "A file with this name is already in the folder"
                else -> null
            }
            PlanItem(file.name, newName, problem)
        }
    }

    fun pad(n: Int, digits: Int): String = n.toString().padStart(digits.coerceIn(1, 6), '0')

    /** 每个单词首字母大写，其余小写；单词按空格、下划线、连字符分隔，分隔符保留 */
    fun titleCase(text: String): String {
        val out = StringBuilder(text.length)
        var startOfWord = true
        for (c in text) {
            out.append(if (startOfWord) c.uppercaseChar() else c.lowercaseChar())
            startOfWord = c == ' ' || c == '_' || c == '-'
        }
        return out.toString()
    }
}
