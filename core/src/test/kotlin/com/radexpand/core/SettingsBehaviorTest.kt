package com.radexpand.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ayarlardan degistirilebilen davranislar (A1-A4), doldurma alanlari (C1), tarih (C2), kelime uyarisi (C4). */
class SettingsBehaviorTest {

    private val base = listOf(
        Shortcut("ri", "hiperintensite"),
        Shortcut("bel", "belirginleşme", mode = ExpandMode.SPECIAL_ONLY),
        Shortcut("nsi", "Normal sınırlarda inceleme"),
        Shortcut("ivd", "intervertebral disk", mode = ExpandMode.AUTO, wordBoundary = false),
    )

    private fun cfg(s: AppSettings) = s.toEngineConfig()
    private fun run(s: AppSettings, seq: String, sc: List<Shortcut> = base, ed: FakeEditor = FakeEditor()) =
        Sim(sc, ed, cfg(s)).type(seq).text

    // ---------------------------------------------------------------- README ornekleri birebir

    @Test fun `README ornekleri yazildigi gibi calisir`() {
        val tr = listOf(Shortcut("rp", "rapor"))
        val s = AppSettings()
        assertEquals("rapor ", run(s, "rp␣", tr))
        assertEquals("rapor\n", run(s, "rp⏎", tr))
        assertEquals("raporlar", run(s, "rp⇥lar", tr))
        assertEquals("turp ", run(s, "turp␣", tr))
        assertEquals("Rapor ", run(s, "Rp␣", tr))
        assertEquals("rapor ", run(AppSettings(caseMode = CaseMode.KEEP_TEXT), "Rp␣", tr))
        assertEquals("rp ", run(s, "rp␣⌫", tr))

        val en = listOf(Shortcut("rpt", "report"))
        assertEquals("report ", run(s, "rpt␣", en))
        assertEquals("reports", run(s, "rpt⇥s", en))
        assertEquals("excerpt ", run(s, "excerpt␣", en))
        assertEquals("Report ", run(s, "Rpt␣", en))
        assertEquals("report ", run(AppSettings(caseMode = CaseMode.KEEP_TEXT), "Rpt␣", en))
    }

    // ---------------------------------------------------------------- A1 tus davranislari

    @Test fun `varsayilan ayarlar eski davranisla ayni`() {
        val s = AppSettings()
        assertEquals("hiperintensite ", run(s, "ri␣"))
        assertEquals("hiperintensite\n", run(s, "ri⏎"))
        assertEquals("hiperintensiteler", run(s, "ri⇥ler"))
    }

    @Test fun `Space kapali`() = assertEquals("ri hiperintensite", run(AppSettings(spaceAction = KeyAction.OFF), "ri␣ri⇥"))

    @Test fun `Space yalnizca genislet - bosluk eklenmez`() =
        assertEquals("hiperintensiteler", run(AppSettings(spaceAction = KeyAction.EXPAND_ONLY), "ri␣ler"))

    @Test fun `Enter yalnizca genislet - yeni satir yok`() =
        assertEquals("hiperintensite", run(AppSettings(enterAction = KeyAction.EXPAND_ONLY), "ri⏎"))

    @Test fun `Enter kapali`() = assertEquals("ri\n", run(AppSettings(enterAction = KeyAction.OFF), "ri⏎"))

    @Test fun `Tab genislet + tab`() {
        val ed = FakeEditor(tabInsertsTab = false)
        assertEquals("hiperintensite", run(AppSettings(tabAction = KeyAction.EXPAND_WITH_KEY), "ri⇥", ed = ed))
        assertEquals(1, ed.tabsSent) // Tab'in kendi isi (sonraki alana gecis) yapildi
    }

    @Test fun `Tab kapali - yalnizca Tab kisayolu da genislemez`() =
        assertEquals("bel\t", run(AppSettings(tabAction = KeyAction.OFF), "bel⇥"))

    @Test fun `yalnizca Tab kisayolu Tab+tab ayarinda bile tusun isini yapmaz`() {
        val ed = FakeEditor(tabInsertsTab = false)
        assertEquals("belirginleşmeler", run(AppSettings(tabAction = KeyAction.EXPAND_WITH_KEY), "bel⇥ler", ed = ed))
        assertEquals(0, ed.tabsSent)
    }

    // ---------------------------------------------------------------- A2 ozel tus

    @Test fun `ozel tus tanimli degilse hicbir sey yapmaz`() = assertEquals("ri", run(AppSettings(), "ri★"))

    @Test fun `ozel tus genisletir, tusun kendi isi yoktur`() {
        val s = AppSettings(specialKey = "131:0", specialKeyLabel = "F1")
        assertEquals("hiperintensiteler", run(s, "ri★ler"))
        assertEquals("belirginleşme", run(s, "bel★"))
    }

    @Test fun `yalnizca Tab kisayolu sadece ozel tusla`() {
        val s = AppSettings(specialKey = "131:0", tabForSpecialOnly = false)
        assertEquals("bel\t", run(s, "bel⇥"))
        assertEquals("belirginleşme", run(s, "bel★"))
    }

    // ---------------------------------------------------------------- A3 kelime kurallari, buyuk harf

    @Test fun `kesme isareti kelimeden sayilmazsa ek sonrasi genisler`() {
        assertEquals("MR'ri ", run(AppSettings(), "MR'ri␣"))
        assertEquals("MR'hiperintensite ", run(AppSettings(apostropheIsWord = false), "MR'ri␣"))
    }

    @Test fun `tire kelimeye dahilse genislemez`() {
        assertEquals("x-hiperintensite ", run(AppSettings(), "x-ri␣"))
        assertEquals("x-ri ", run(AppSettings(hyphenIsWord = true), "x-ri␣"))
    }

    @Test fun `buyuk harf modlari`() {
        assertEquals("Ri ", run(AppSettings(caseMode = CaseMode.OFF), "Ri␣"))
        assertEquals("Hiperintensite ", run(AppSettings(caseMode = CaseMode.CAPITAL), "Ri␣"))
        // "Metin olduğu gibi": büyük harfle yazılan kısayol genişler, metin değişmeden yazılır
        assertEquals("hiperintensite ", run(AppSettings(caseMode = CaseMode.KEEP_TEXT), "Ri␣"))
        assertEquals("NSİ ", run(AppSettings(caseMode = CaseMode.KEEP_TEXT), "NSİ␣"))
        assertEquals("hiperintensite ", run(AppSettings(caseMode = CaseMode.KEEP_TEXT), "ri␣"))
        assertEquals("NSİ ", run(AppSettings(caseMode = CaseMode.CAPITAL), "NSİ␣"))
        assertEquals("NORMAL SINIRLARDA İNCELEME ", run(AppSettings(caseMode = CaseMode.FULL), "NSİ␣"))
        assertEquals("HİPERİNTENSİTE ", run(AppSettings(minUpperLetters = 2), "Rİ␣"))
    }

    @Test fun `noktalama isaretleri ayarlanabilir`() {
        val s = AppSettings(punctuationTriggers = true, punctuationChars = ",")
        assertEquals("hiperintensite, ri.", run(s, "ri, ri."))
    }

    // ---------------------------------------------------------------- C1 doldurma alanlari, C2 tarih

    private val tmpl = listOf(Shortcut("kd", "Karaciğer \$|\$ mm, dalak \$|\$ mm, safra kesesi \$|\$."))

    @Test fun `birden fazla alan Tab ile doldurulur`() {
        assertEquals("Karaciğer 152 mm, dalak 118 mm, safra kesesi normal.", run(AppSettings(), "kd␣152⇥118⇥normal", tmpl))
    }

    @Test fun `alan icinde silme yapilsa da sonraki alan dogru bulunur`() {
        assertEquals("Karaciğer 15 mm, dalak 9 mm, safra kesesi .", run(AppSettings(), "kd␣152⌫⇥9", tmpl))
    }

    @Test fun `son alandan sonra Tab normal calisir`() {
        assertEquals("Karaciğer 1 mm, dalak 2 mm, safra kesesi 3\t.", run(AppSettings(), "kd␣1⇥2⇥3⇥", tmpl))
    }

    @Test fun `alan icinde kisayol genisler ve gezinme devam eder`() {
        assertEquals("Karaciğer hiperintensite mm, dalak 5 mm, safra kesesi .", run(AppSettings(), "kd␣ri⇥⇥5", tmpl + base))
    }

    @Test fun `ok tusu alan gezinmesini bitirir`() {
        val t = run(AppSettings(), "kd␣1←⇥", tmpl)
        assertTrue(t.contains("\t"), t)
    }

    @Test fun `tarih ve saat degiskenleri`() {
        val sc = listOf(Shortcut("trh", "Tetkik tarihi: {tarih} {saat}"), Shortcut("dt", "{date}"))
        assertEquals("Tetkik tarihi: 06.11.2026 09:05 ", run(AppSettings(), "trh␣", sc))
        assertEquals("06.11.2026 ", run(AppSettings(), "dt␣", sc))
        assertEquals("TETKİK TARİHİ: 06.11.2026 09:05 ", run(AppSettings(), "TRH␣", sc))
    }

    // ---------------------------------------------------------------- C4 gercek kelime uyarisi, A6 ice aktarma

    @Test fun `gercek kelime ve birim uyarisi`() {
        val d = RadExpandData()
        assertTrue(DataOps.validate(d, ShortcutRecord(trigger = "ön", replacement = "önerilir.")).any { "günlük dilde" in it })
        assertTrue(DataOps.validate(d, ShortcutRecord(trigger = "cc", replacement = "kraniokaudal")).any { "birim" in it })
        assertTrue(DataOps.validate(d, ShortcutRecord(trigger = "ön", replacement = "x", mode = ExpandMode.SPECIAL_ONLY)).isEmpty())
        assertTrue(DataOps.validate(d, ShortcutRecord(trigger = "t2r", replacement = "x")).isEmpty())
    }

    @Test fun `word olmayan Espanso kisayollarinin aktarim secenegi`() {
        val y = "matches:\n  - trigger: kat\n    replace: S\n"
        assertEquals(ExpandMode.AUTO, EspansoImport.parse("a.yml", y, 1).records[0].mode)
        assertEquals(false, EspansoImport.parse("a.yml", y, 1, NoWordImport.AUTO_ANYWHERE).records[0].wordBoundary)
        assertEquals(ExpandMode.MANUAL, EspansoImport.parse("a.yml", y, 1, NoWordImport.MANUAL).records[0].mode)
    }

    @Test fun `eski ayar dosyasi yeni alanlarla okunur`() {
        val old = """{"format":"medexpand","version":1,"shortcuts":[],"settings":{"enabled":true,"punctuationTriggers":true}}"""
        val d = DataCodec.decode(old)
        assertTrue(d.settings.punctuationTriggers)
        assertEquals(KeyAction.EXPAND_ONLY, d.settings.tabAction)
        assertEquals("", d.settings.language)
    }
}
