package com.orbitclean.app.ui

import com.appbase.core.storage.FileGroup
import com.appbase.core.storage.FileItem
import com.appbase.core.storage.StorageInfo
import com.orbitclean.app.data.FileKind
import com.orbitclean.app.ui.done.StorageDelta
import com.orbitclean.app.ui.done.recommendsFor
import com.orbitclean.app.ui.scan.ScanUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStateTest {

    private fun item(name: String, size: Long) = FileItem("/sdcard/$name", name, size, 0L)
    private fun group(vararg items: FileItem) = FileGroup(null, items.toList())

    @Test
    fun scanIsPendingUntilEveryKindHasResults() {
        val ui = ScanUi.from(mapOf(FileKind.REDUNDANT to listOf(group(item("a", 10)))))

        assertFalse(ui.done)
        assertEquals(1, ui.rows.first { it.kind == FileKind.REDUNDANT }.count)
        assertNull(ui.rows.first { it.kind == FileKind.DUPLICATE }.count)
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
    fun storageDeltaAddsFreedBytesBackForTheBeforeValue() {
        val delta = StorageDelta.of(StorageInfo(total = 1000, free = 400), freed = 100)

        assertEquals(0.7f, delta.before, 1e-4f)
        assertEquals(0.6f, delta.after, 1e-4f)
    }

    @Test
    fun storageDeltaHandlesUnknownTotal() {
        assertEquals(StorageDelta(0f, 0f), StorageDelta.of(StorageInfo(total = 0, free = 0), freed = 5))
    }

    @Test
    fun recommendsSkipTheCleanedKindAndIgnoreEmptyResults() {
        val recs = recommendsFor(
            FileKind.REDUNDANT,
            mapOf(FileKind.DUPLICATE to emptyList(), FileKind.LARGE to listOf(group(item("x", 7)))),
        )

        assertEquals(listOf(FileKind.DUPLICATE, FileKind.LARGE, FileKind.RECENT), recs.map { it.kind })
        assertNull(recs[0].found)
        assertEquals(1, recs[1].found)
        assertEquals(7L, recs[1].size)
    }
}
