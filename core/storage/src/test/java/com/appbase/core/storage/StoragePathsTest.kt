package com.appbase.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePathsTest {
    private val root = "/storage/emulated/0"

    @Test
    fun docIdToPath() {
        assertEquals("$root/DCIM/Camera", StoragePaths.pathOf("primary:DCIM/Camera", root))
        assertEquals(root, StoragePaths.pathOf("primary:", root))
        assertEquals("/storage/1A2B-3C4D/Music", StoragePaths.pathOf("1A2B-3C4D:Music", root))
        assertNull(StoragePaths.pathOf("no-colon", root))
    }

    @Test
    fun pathToDocId() {
        assertEquals("primary:DCIM/Camera", StoragePaths.docIdOf("$root/DCIM/Camera", root))
        assertEquals("primary:", StoragePaths.docIdOf(root, root))
        assertEquals("1A2B-3C4D:Music", StoragePaths.docIdOf("/storage/1A2B-3C4D/Music", root))
        assertEquals("1A2B-3C4D:", StoragePaths.docIdOf("/storage/1A2B-3C4D", root))
        assertNull(StoragePaths.docIdOf("/data/user/0/x", root))
        assertNull(StoragePaths.docIdOf("/storage/emulated/10/x", root))
    }

    @Test
    fun roundTrip() {
        for (id in listOf("primary:Download/Work/a b (1).pdf", "primary:DCIM", "ABCD-1234:x/y")) {
            assertEquals(id, StoragePaths.docIdOf(StoragePaths.pathOf(id, root)!!, root))
        }
    }

    @Test
    fun coversOnlyWholeSegments() {
        assertTrue(StoragePaths.covers("$root/DCIM", "$root/DCIM"))
        assertTrue(StoragePaths.covers("$root/DCIM", "$root/DCIM/Camera/a.jpg"))
        assertFalse(StoragePaths.covers("$root/DCIM", "$root/DCIM2/a.jpg"))
    }

    @Test
    fun display() {
        assertEquals("Internal storage/DCIM/Camera", StoragePaths.display("$root/DCIM/Camera", root))
        assertEquals("Internal storage", StoragePaths.display(root, root))
        assertEquals("SD card/Music", StoragePaths.display("/storage/1A2B-3C4D/Music", root))
    }
}
