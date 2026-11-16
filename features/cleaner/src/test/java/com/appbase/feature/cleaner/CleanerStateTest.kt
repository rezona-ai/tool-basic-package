package com.appbase.feature.cleaner

import com.appbase.core.storage.FileGroup
import com.appbase.core.storage.FileItem
import com.appbase.core.storage.StorageInfo
import com.appbase.feature.cleaner.data.ReminderReason
import com.appbase.feature.cleaner.data.ReminderRules
import com.appbase.feature.cleaner.state.ScanUi
import com.appbase.feature.cleaner.state.StorageDelta
import com.appbase.feature.cleaner.state.defaultSelection
import com.appbase.feature.cleaner.state.recommendsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanerStateTest {

    private fun item(name: String, size: Long) = FileItem("/sdcard/$name", name, size, 0L)
    private fun group(vararg items: FileItem) = FileGroup(null, items.toList())

    @Test
    fun scanIsPendingUntilEveryKindHasResults() {
        val ui = ScanUi.from(mapOf(FileKind.REDUNDANT to listOf(group(item("a", 10)))))
        assertFalse(ui.done)
        assertEquals(1, ui.rows.first { it.kind == FileKind.REDUNDANT }.count)
        assertNull(ui.rows.first { it.kind == FileKind.DUPLICATE }.count)
        assertEquals(1f / 3, ui.progress, 1e-4f)
    }

    @Test
    fun junkCountsAllRedundantAndAllButOneCopyOfDuplicates() {
        val ui = ScanUi.from(
            mapOf(
                FileKind.REDUNDANT to listOf(group(item("a", 100), item("b", 50))),
                FileKind.DUPLICATE to listOf(group(item("c1", 30), item("c2", 30), item("c3", 30))),
                FileKind.LARGE to listOf(group(item("big", 10_000))),
            ),
        )
        assertTrue(ui.done)
        assertEquals(100L + 50L + 30L + 30L, ui.junkBytes)
        assertEquals(6, ui.itemCount)
    }

    @Test
    fun scanWithCustomKindsOnlyCountsThoseKinds() {
        val ui = ScanUi.from(mapOf(FileKind.REDUNDANT to listOf(group(item("a", 100)))), listOf(FileKind.LARGE))
        assertEquals(0L, ui.junkBytes)
        assertEquals(listOf(FileKind.LARGE), ui.rows.map { it.kind })
    }

    @Test
    fun defaultSelectionKeepsFirstDuplicateAndSkipsLargeFiles() {
        val dup = listOf(group(item("c1", 30), item("c2", 30)))
        assertEquals(setOf("/sdcard/c2"), defaultSelection(FileKind.DUPLICATE, dup))
        assertEquals(setOf("/sdcard/a"), defaultSelection(FileKind.REDUNDANT, listOf(group(item("a", 1)))))
        assertTrue(defaultSelection(FileKind.LARGE, listOf(group(item("big", 1)))).isEmpty())
    }

    @Test
    fun storageDeltaAddsFreedBytesBack() {
        val delta = StorageDelta.of(StorageInfo(total = 1000, free = 400), freed = 100)
        assertEquals(0.7f, delta.before, 1e-4f)
        assertEquals(0.6f, delta.after, 1e-4f)
        assertEquals(StorageDelta(0f, 0f), StorageDelta.of(StorageInfo(total = 0, free = 0), freed = 5))
    }

    @Test
    fun recommendsSkipCleanedKindsAndIgnoreEmptyResults() {
        val recs = recommendsFor(
            setOf(FileKind.REDUNDANT),
            mapOf(FileKind.DUPLICATE to emptyList(), FileKind.LARGE to listOf(group(item("x", 7)))),
        )
        assertEquals(listOf(FileKind.DUPLICATE, FileKind.LARGE, FileKind.RECENT), recs.map { it.kind })
        assertNull(recs[0].found)
        assertEquals(1, recs[1].found)
        assertEquals(7L, recs[1].size)
        // 一键清理后三类都清过：推荐从按类型浏览开始
        val after = recommendsFor(FileKind.CLEANABLE.toSet(), emptyMap())
        assertEquals(listOf(FileKind.RECENT, FileKind.IMAGES, FileKind.VIDEOS), after.map { it.kind })
    }

    @Test
    fun reminderPriority() {
        assertTrue(ReminderRules.decide(5, 1, ReminderRules.JUNK_THRESHOLD, 10, false) is ReminderReason.LowStorage)
        assertTrue(ReminderRules.decide(50, 1, ReminderRules.JUNK_THRESHOLD, 10, false) is ReminderReason.Junk)
        assertEquals(ReminderReason.Stale(3), ReminderRules.decide(50, 1, 0, 3, false))
        assertNull(ReminderRules.decide(50, 1, 0, 1, false))
        assertTrue(ReminderRules.decide(50, 1, 0, 1, true) is ReminderReason.Test)
    }
}
