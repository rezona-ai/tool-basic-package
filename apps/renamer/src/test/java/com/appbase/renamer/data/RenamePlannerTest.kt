package com.appbase.renamer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class RenamePlannerTest {

    private val utc = ZoneOffset.UTC
    private fun f(name: String, modified: Long = 0L) = SourceFile(name, modified)

    @Test
    fun splitKeepsExtension() {
        assertEquals("photo" to ".jpg", FileNames.split("photo.jpg"))
        assertEquals("archive.tar" to ".gz", FileNames.split("archive.tar.gz"))
        assertEquals(".nomedia" to "", FileNames.split(".nomedia"))
        assertEquals("README" to "", FileNames.split("README"))
        assertEquals("trailing." to "", FileNames.split("trailing."))
    }

    @Test
    fun numberRuleUsesSortedOrderAndPadding() {
        val files = RenamePlanner.sorted(listOf(f("b.jpg"), f("A.png"), f("c.JPG")), SortOrder.NAME)
        val rule = RenameRule(RuleType.NUMBER, baseName = "Trip", start = 9, digits = 3, separator = "_")
        assertEquals(listOf("Trip_009.png", "Trip_010.jpg", "Trip_011.JPG"), RenamePlanner.newNames(files, rule, utc))
    }

    @Test
    fun sortByModifiedThenName() {
        val files = RenamePlanner.sorted(listOf(f("z.txt", 1), f("a.txt", 2), f("b.txt", 1)), SortOrder.MODIFIED)
        assertEquals(listOf("b.txt", "z.txt", "a.txt"), files.map { it.name })
    }

    @Test
    fun dateRuleOriginalAndNumber() {
        val march14 = 1_710_412_200_000L // 2024-03-14T10:30:00Z
        val files = listOf(f("scan.pdf", march14), f("note.pdf", march14))
        val original = RenameRule(RuleType.DATE, dateFormat = DateFormat.DASHED, dateThen = DateThen.ORIGINAL, separator = "_")
        assertEquals(listOf("2024-03-14_scan.pdf", "2024-03-14_note.pdf"), RenamePlanner.newNames(files, original, utc))
        val numbered = original.copy(dateFormat = DateFormat.COMPACT, dateThen = DateThen.NUMBER, digits = 2, start = 1, separator = "-")
        assertEquals(listOf("20240314-01.pdf", "20240314-02.pdf"), RenamePlanner.newNames(files, numbered, utc))
        val withTime = original.copy(dateFormat = DateFormat.WITH_TIME, separator = " ")
        assertEquals("2024-03-14_1030 scan.pdf", RenamePlanner.newNames(files.take(1), withTime, utc).single())
    }

    @Test
    fun affixReplaceAndCase() {
        val one = listOf(f("IMG 0041.jpg"))
        assertEquals("Q3_IMG 0041_final.jpg", RenamePlanner.newNames(one, RenameRule(RuleType.AFFIX, prefix = "Q3_", suffix = "_final"), utc).single())
        assertEquals("Paris 0041.jpg", RenamePlanner.newNames(one, RenameRule(RuleType.REPLACE, find = "img", replace = "Paris", ignoreCase = true), utc).single())
        assertEquals("IMG 0041.jpg", RenamePlanner.newNames(one, RenameRule(RuleType.REPLACE, find = "img", replace = "Paris", ignoreCase = false), utc).single())
        assertEquals("0041.jpg", RenamePlanner.newNames(one, RenameRule(RuleType.REPLACE, find = "IMG ", replace = ""), utc).single())
        val title = RenameRule(RuleType.CASE, caseMode = CaseMode.TITLE)
        // 空格、下划线、连字符后面都算新单词
        assertEquals("My Holiday_Photo-Set.JPG", RenamePlanner.newNames(listOf(f("my HOLIDAY_PHOTO-SET.JPG")), title, utc).single())
        assertEquals("my notes.TXT", RenamePlanner.newNames(listOf(f("My Notes.TXT")), RenameRule(RuleType.CASE, caseMode = CaseMode.LOWER), utc).single())
    }

    @Test
    fun planFlagsDuplicatesInBatchCaseInsensitively() {
        val files = listOf(f("a-1.txt"), f("A_1.txt"))
        val rule = RenameRule(RuleType.REPLACE, find = "-", replace = "_")
        val plan = RenamePlanner.plan(files, rule, others = emptyList(), zone = utc)
        // "a-1.txt" → "a_1.txt"，和没改的 "A_1.txt" 只差大小写，在手机存储上算同一个名字
        assertNotNull(plan.first { it.oldName == "a-1.txt" }.problem)
        assertNull(plan.first { it.oldName == "A_1.txt" }.problem)
    }

    @Test
    fun planFlagsCollisionWithUnselectedFile() {
        val plan = RenamePlanner.plan(listOf(f("draft.txt")), RenameRule(RuleType.AFFIX, suffix = "_v2"), others = listOf("DRAFT_V2.txt"), zone = utc)
        assertEquals("A file with this name is already in the folder", plan.single().problem)
    }

    @Test
    fun planFlagsIllegalAndEmptyNames() {
        assertNotNull(RenamePlanner.plan(listOf(f("a.txt")), RenameRule(RuleType.AFFIX, prefix = "x/"), emptyList(), utc).single().problem)
        assertNotNull(RenamePlanner.plan(listOf(f("abc.txt")), RenameRule(RuleType.REPLACE, find = "abc", replace = ""), emptyList(), utc).single().problem)
        assertNotNull(RenamePlanner.plan(listOf(f("a.txt")), RenameRule(RuleType.AFFIX, suffix = "x".repeat(300)), emptyList(), utc).single().problem)
        assertNotNull(FileNames.problem(" lead.txt"))
        assertNull(FileNames.problem("Trip_001.jpg"))
    }

    @Test
    fun collisionWithUnchangedFileButCaseOnlyChangeAllowed() {
        // x.txt → y.txt，而 y.txt 在这条规则下不变：同一批里重名，要报问题
        val files = listOf(f("x.txt"), f("y.txt"))
        val clash = RenamePlanner.plan(files, RenameRule(RuleType.REPLACE, find = "x", replace = "y"), emptyList(), utc)
        assertNotNull(clash.first { it.oldName == "x.txt" }.problem)
        // 只改大小写不算和自己重名（执行时走两段式，能成功）
        val upper = RenamePlanner.plan(listOf(f("notes.txt")), RenameRule(RuleType.CASE, caseMode = CaseMode.UPPER), emptyList(), utc)
        assertEquals("NOTES.txt", upper.single().newName)
        assertNull(upper.single().problem)
    }

    @Test
    fun unchangedNamesAreNotProblems() {
        val plan = RenamePlanner.plan(listOf(f("same.txt")), RenameRule(RuleType.REPLACE, find = "zzz", replace = "q"), emptyList(), utc)
        assertEquals(false, plan.single().changed)
        assertNull(plan.single().problem)
    }

    @Test
    fun tempNameKeepsExtensionAndIsNotHidden() {
        val temp = Renamer.tempName(42L, 3, "photo.jpg")
        assertEquals("namewise_tmp_42_3.jpg", temp)
    }
}
