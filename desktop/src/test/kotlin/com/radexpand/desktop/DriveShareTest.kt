package com.radexpand.desktop

import com.radexpand.core.DataCodec
import com.radexpand.core.RadExpandData
import com.radexpand.core.ShortcutRecord
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DriveShareTest {
    private fun rec(id: String, trigger: String, at: Long) = ShortcutRecord(id = id, trigger = trigger, replacement = trigger.uppercase(), updatedAt = at)

    @Test fun `gonder, degistir, al akisi`() {
        assertTrue(System.getenv("RADEXPAND_HOME") != null, "testler ayri veri klasorunde calismali")
        val dl = Files.createTempDirectory("rx-dl").toFile()
        DriveShare.downloadsOverride = dl
        DesktopStore.update { it.copy(shortcuts = listOf(rec("a", "aa", 1))) }

        // Ozellik hic kullanilmadiysa hatirlatma yok
        assertFalse(DriveShare.isPending(DesktopStore.data.value, DesktopStore.prefs.value))

        val f = DriveShare.send(openBrowser = false, reveal = false)
        assertEquals(DriveShare.FILE_NAME, f.name)
        assertFalse(DriveShare.isPending(DesktopStore.data.value, DesktopStore.prefs.value))

        // Bu bilgisayarda yeni kisayol -> gonderilmemis degisiklik
        DesktopStore.update { it.copy(shortcuts = it.shortcuts + rec("b", "bb", 2)) }
        assertTrue(DriveShare.isPending(DesktopStore.data.value, DesktopStore.prefs.value))

        // Baska bilgisayardan gelen dosya: a guncellenmis + c eklenmis (b yok)
        val other = File2.write(dl, "radexpand-kisayollar (1).json", listOf(rec("a", "aa", 5).copy(replacement = "YENI"), rec("c", "cc", 3)))
        assertEquals(other, DriveShare.latestDownloaded())
        DriveShare.receive(other)
        val now = DesktopStore.data.value.shortcuts.associateBy { it.id }
        assertEquals("YENI", now["a"]!!.replacement)
        assertTrue("b" in now && "c" in now)
        // b dosyada yok: hala gonderilmemis degisiklik var
        assertTrue(DriveShare.isPending(DesktopStore.data.value, DesktopStore.prefs.value))

        DriveShare.send(openBrowser = false, reveal = false)
        assertFalse(DriveShare.isPending(DesktopStore.data.value, DesktopStore.prefs.value))
        assertEquals(3, DataCodec.decode(f.readText()).shortcuts.size)
        dl.deleteRecursively()
    }

    private object File2 {
        fun write(dir: java.io.File, name: String, list: List<ShortcutRecord>): java.io.File {
            val f = java.io.File(dir, name)
            f.writeText(DataCodec.encode(RadExpandData(shortcuts = list)))
            f.setLastModified(System.currentTimeMillis() + 5_000)
            return f
        }
    }
}
