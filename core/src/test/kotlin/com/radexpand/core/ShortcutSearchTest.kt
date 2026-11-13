package com.radexpand.core

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShortcutSearchTest {
    private val list = listOf(
        Shortcut("rp", "rapor", category = "Genel"),
        Shortcut("rpt", "report", category = "Genel"),
        Shortcut("tpl", "Toplantı \$|\$ tarihinde, \$|\$ salonunda.", category = "Şablon"),
        Shortcut("trh", "Tarih: {tarih}", category = "Şablon"),
        Shortcut("kapali", "x", enabled = false),
    )

    @Test fun `bos aramada cok kullanilanlar once`() {
        val r = ShortcutSearch.find(list, "", mapOf("tpl" to 5, "rp" to 2))
        assertEquals(listOf("tpl", "rp"), r.take(2).map { it.trigger })
        assertTrue(r.none { it.trigger == "kapali" })
    }

    @Test fun `kisayolla baslayanlar once, metinde gecenler sonra`() {
        val r = ShortcutSearch.find(list, "rp")
        assertEquals(listOf("rp", "rpt"), r.map { it.trigger })
        assertEquals(listOf("rp"), ShortcutSearch.find(list, "rapor").map { it.trigger })
        assertEquals(listOf("tpl", "trh"), ShortcutSearch.find(list, "şablon").map { it.trigger }.sorted())
    }

    @Test fun `Turkce buyuk kucuk harf ve birden cok kelime`() {
        assertEquals(listOf("tpl"), ShortcutSearch.find(list, "TOPLANTI salon").map { it.trigger })
    }

    @Test fun `cikti degisken ve imlec alani islenir`() {
        val now = LocalDateTime.of(2026, 11, 13, 9, 5)
        assertEquals("Tarih: 13.11.2026" to 0, ShortcutSearch.output(list[3], now))
        val (text, back) = ShortcutSearch.output(list[2], now)
        assertEquals("Toplantı  tarihinde,  salonunda.", text)
        assertEquals(text.length - "Toplantı ".length, back)
    }

    @Test fun `arama tusu varsayilan Alt Space`() {
        assertEquals(KeyCombo(62, KeyCombo.ALT), AppSettings().searchCombo(62))
        assertEquals(null, AppSettings(searchKeyEnabled = false).searchCombo(62))
    }
}
