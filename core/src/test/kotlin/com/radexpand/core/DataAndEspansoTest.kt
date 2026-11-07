package com.radexpand.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataAndEspansoTest {

    private fun rec(t: String, r: String, at: Long = 1, id: String = "id-$t", mode: ExpandMode = ExpandMode.MANUAL) =
        ShortcutRecord(id = id, trigger = t, replacement = r, updatedAt = at, mode = mode)

    // ---------------------------------------------------------------- veri dosyasi

    @Test fun `veri dosyasi kaydedilip geri okunur`() {
        val d = RadExpandData(shortcuts = listOf(rec("ri", "hiperintens"), rec("rtü", "Raporda\n\nDr. Ü \"x\"")),
            settings = AppSettings(punctuationTriggers = true, disabledPackages = setOf("a.b")))
        val back = DataCodec.decode(DataCodec.encode(d))
        assertEquals(d, back)
    }

    @Test fun `ayni kisayolda en yeni kayit kazanir`() {
        val d = RadExpandData(shortcuts = listOf(rec("ri", "eski", at = 1, id = "a"), rec("ri", "yeni", at = 5, id = "b")))
        assertEquals("yeni", d.buildIndex().byTrigger["ri"]?.replacement)
    }

    @Test fun `silinen ve pasif kayitlar dizine girmez`() {
        var d = RadExpandData(shortcuts = listOf(rec("ri", "x"), rec("oi", "y"), rec("iz", "z")))
        d = DataOps.delete(d, setOf("id-ri"), 10)
        d = DataOps.setEnabled(d, setOf("id-oi"), false, 10)
        assertEquals(setOf("iz"), d.buildIndex().byTrigger.keys)
        assertEquals(3, d.shortcuts.size) // mezar tasi duruyor
        d = DataOps.restore(d, setOf("id-ri"), 11)
        assertTrue("ri" in d.buildIndex().byTrigger)
    }

    @Test fun `dogrulama uyarilari`() {
        val d = RadExpandData(shortcuts = listOf(rec("ri", "x"), rec("abc", "y")))
        assertTrue(DataOps.validate(d, rec("ri", "z", id = "yeni")).any { "zaten var" in it })
        assertTrue(DataOps.validate(d, rec("a b", "z", id = "yeni")).any { "boşluk" in it })
        assertTrue(DataOps.validate(d, rec("ab", "z", id = "yeni", mode = ExpandMode.AUTO)).any { "hiç yazılamaz" in it })
        assertTrue(DataOps.validate(d, rec("qzx", "z", id = "yeni")).isEmpty())
    }

    // ---------------------------------------------------------------- senkron

    @Test fun `senkron - yeni olan kazanir, silme yayilir, hicbir sey kaybolmaz`() {
        val local = listOf(rec("ri", "tablet", at = 10, id = "1"), rec("oi", "x", at = 1, id = "2"), rec("yalnizTablet", "t", at = 3, id = "3"))
        val remote = listOf(rec("ri", "pc", at = 5, id = "1"), rec("oi", "x", at = 9, id = "2").copy(deleted = true), rec("yalnizPc", "p", at = 4, id = "4"))
        val r = SyncMerge.merge(local, remote)
        val byId = r.shortcuts.associateBy { it.id }
        assertEquals("tablet", byId["1"]?.replacement)
        assertTrue(byId["2"]!!.deleted)
        assertNotNull(byId["3"])
        assertNotNull(byId["4"])
        assertEquals(2, r.fromRemote) // silme + yeni
        assertTrue(r.changed)
    }

    @Test fun `senkron - ayni veride degisiklik yok`() {
        val l = listOf(rec("ri", "x", at = 3))
        val r = SyncMerge.merge(l, l)
        assertFalse(r.changed)
        assertEquals(l, r.shortcuts)
    }

    // ---------------------------------------------------------------- Espanso ice aktarma

    private val sample = """
        matches:

        #KİŞİSEL

          - trigger: "u1"
            replace: "a@b.com"
            word: true

          - trigger: "drimza"
            replace: "Dr. Ayşe ÖZTÜRK"

        #GENEL TERMİNOLOJİ

          - trigger: "rtü"
            replace: "Raporda tanımlı bulgular


                      Dr. Ayşe ÖZTÜRK"

          - trigger: "dlm"
            replace: daralma""
            word: true

          - trigger: "hkb"
            replace: "havalanma kaybı"

          - trigger: "hkb"
            replace: "havalanma kaybı"

        ### NÖRO
          - triggers: ["p1", "ax"]
            replace: "aksiyel"
            word: true
          - trigger: "kccc"
            replace: "Karaciğer kraniokaudal boyutu ${'$'}|${'$'} mm"
            word: true
          - trigger: ":tarih"
            replace: "{{tarih}}"
            vars:
              - name: tarih
                type: date
          - trigger: ":form"
            form: "Ad: [[ad]]"
          - regex: "(?P<x>\\d+)mm"
            replace: "{{x}} mm"
          - trigger: "lw"
            replace: "left"
            left_word: true
          - trigger: "rw"
            replace: "right"
            right_word: true
          - trigger: "blok"
            replace: |
              Satır 1
              # bu bir yorum değil, metin
              Satır 2
          - trigger: "son"
            replace: "son"
            uppercase_style: capitalize_words
    """.trimIndent()

    @Test fun `ornek Espanso dosyasi`() {
        val rep = EspansoImport.parse("base.yml", sample, now = 100)
        assertNull(rep.error)
        val by = rep.records.associateBy { it.trigger }

        // word: true -> Space/Enter/Tab
        assertEquals(ExpandMode.MANUAL, by["u1"]!!.mode)
        assertTrue(by["u1"]!!.wordBoundary)
        // word yok -> otomatik + kelime basinda (kullanici karari)
        assertEquals(ExpandMode.AUTO, by["drimza"]!!.mode)
        assertTrue(by["drimza"]!!.wordBoundary)
        // cok satirli cift tirnak: YAML katlama kurallari (Espanso ile ayni sonuc)
        assertEquals("Raporda tanımlı bulgular\n\nDr. Ayşe ÖZTÜRK", by["rtü"]!!.replacement)
        // tirnak hatasi oldugu gibi aktarilir ama uyarilir
        assertEquals("daralma\"\"", by["dlm"]!!.replacement)
        assertTrue(rep.warnings.any { it.trigger == "dlm" && "tırnak" in it.message })
        // ayni kayit iki kez -> bir kez
        assertEquals(1, rep.records.count { it.trigger == "hkb" })
        assertTrue(rep.warnings.any { it.trigger == "hkb" && "iki kez" in it.message })
        // triggers listesi -> iki kayit
        assertEquals("aksiyel", by["p1"]!!.replacement)
        assertEquals("aksiyel", by["ax"]!!.replacement)
        // imlec isareti korunur
        assertTrue(ExpansionEngine.CURSOR in by["kccc"]!!.replacement)
        // left_word / right_word
        assertEquals(ExpandMode.AUTO, by["lw"]!!.mode)
        assertEquals(ExpandMode.MANUAL, by["rw"]!!.mode)
        assertFalse(by["rw"]!!.wordBoundary)
        // blok metin icindeki # yorum sayilmaz
        assertEquals("Satır 1\n# bu bir yorum değil, metin\nSatır 2\n", by["blok"]!!.replacement)
        // desteklenmeyenler ve nedenleri
        val un = rep.unsupported.associate { it.trigger to it.message }
        assertTrue("Değişken" in un[":tarih"]!!)
        assertTrue("form" in un[":form"]!!)
        assertTrue(rep.unsupported.any { "regex" in it.message })
        assertTrue(rep.warnings.any { it.trigger == "son" && "uppercase_style" in it.message })
        // kategoriler yorumlardan
        assertEquals("KİŞİSEL", by["u1"]!!.category)
        assertEquals("GENEL TERMİNOLOJİ", by["rtü"]!!.category)
        assertEquals("NÖRO", by["kccc"]!!.category)
        assertEquals("NÖRO", by["son"]!!.category)
        // buyuk harf yayma hepsinde acik
        assertTrue(rep.records.all { it.propagateCase })
    }

    @Test fun `bozuk YAML anlasilir hata verir`() {
        val rep = EspansoImport.parse("x.yml", "matches:\n  - trigger: \"a\n    replace: [", now = 1)
        assertNotNull(rep.error)
        assertTrue(rep.records.isEmpty())
    }

    @Test fun `farkli metinle tekrar eden kisayol pasif aktarilir`() {
        val y = "matches:\n  - trigger: a\n    replace: bir\n    word: true\n  - trigger: a\n    replace: iki\n    word: true\n"
        val rep = EspansoImport.parse("x.yml", y, now = 1)
        assertEquals(2, rep.records.size)
        assertTrue(rep.records[0].enabled)
        assertFalse(rep.records[1].enabled)
    }

    @Test fun `imports ve filter notlari`() {
        val y = "imports:\n  - \"../other.yml\"\nfilter_exec: Code.exe\nmatches:\n  - trigger: a\n    replace: b\n"
        val rep = EspansoImport.parse("x.yml", y, now = 1)
        assertTrue(rep.notes.any { "içeri alıyor" in it })
        assertTrue(rep.notes.any { "belirli uygulamalar" in it })
    }

    /** Radyoloji tarzinda uydurma bir Espanso dosyasi (kisisel bilgi yok) ile gercekci davranislar. */
    private val radiologySample = """
        matches:
        # GENEL
          - trigger: "ri"
            replace: "hiperintens"
            word: true
          - trigger: "izş"
            replace: "izlenmiştir."
            word: true
          - trigger: "cc"
            replace: "kraniokaudal"
            word: true
          - trigger: "dan"
            replace: "dansite"
            word: true
        # NÖRO
          - trigger: "kat"
            replace: "Kortikal atrofi ile uyumlu görünüm mevcuttur."
    """.trimIndent()

    @Test fun `radyoloji ornegi ile gercekci davranislar`() {
        val rep = EspansoImport.parse("ornek.yml", radiologySample, now = 1)
        assertNull(rep.error)
        val shortcuts = rep.records.map { it.toShortcut() }
        assertEquals("GENEL", rep.records.first { it.trigger == "ri" }.category)
        assertEquals(ExpandMode.AUTO, rep.records.first { it.trigger == "kat" }.mode)

        val sim = Sim(shortcuts)
        sim.type("bir ri␣odak, izş␣Ri⇥ler cc␣MR'dan␣birinci␣")
        assertEquals("bir hiperintens odak, izlenmiştir. Hiperintensler kraniokaudal MR'dan birinci ", sim.text)
        val sim2 = Sim(shortcuts)
        sim2.type("CC ve MLO, kat")
        assertEquals("CC ve MLO, Kortikal atrofi ile uyumlu görünüm mevcuttur.", sim2.text)
        // Bilinen ve kabul edilen davranis: otomatik kisayolla BASLAYAN bir kelime yazilirken kisayol genisler.
        val sim3 = Sim(shortcuts)
        sim3.type("kat⇤ı")
        assertEquals("katı", sim3.text)
    }

    /**
     * Istege bagli: RADEXPAND_ESPANSO_FILE ortam degiskeni bir Espanso dosyasini gosteriyorsa onu okur.
     * Dosyanin icerigine ozgu bir sey denetlenmez (kisisel dosyalar depoya girmez).
     */
    @Test fun `istege bagli dis Espanso dosyasi`() {
        val path = System.getenv("RADEXPAND_ESPANSO_FILE") ?: return
        val f = File(path)
        if (!f.exists()) return
        val rep = EspansoImport.parse(f.name, f.readText(Charsets.UTF_8), now = 1)
        assertNull(rep.error)
        assertTrue(rep.records.isNotEmpty())
        val sim = Sim(rep.records.map { it.toShortcut() })
        sim.type("birinci␣")
        assertEquals("birinci ", sim.text)
        println("${f.name}: ${rep.records.size} kayıt, ${rep.unsupported.size} aktarılamayan, ${rep.warnings.size} uyarı")
    }
    // ---------------------------------------------------------------- ice aktarma plani

    @Test fun `ice aktarma cakisma secenekleri`() {
        val d = RadExpandData(shortcuts = listOf(rec("ri", "hiperintens", id = "x"), rec("oi", "hipointens", id = "y")))
        val incoming = listOf(rec("ri", "hiperintensite", id = "n1"), rec("oi", "hipointens", id = "n2"), rec("yeni", "Y", id = "n3"))

        val rep = ImportPlanner.plan(d, incoming, ConflictPolicy.REPLACE, now = 50)
        assertEquals(1, rep.added); assertEquals(1, rep.identical); assertEquals(1, rep.replaced)
        assertEquals("hiperintensite", rep.data.active.first { it.trigger == "ri" }.replacement)
        assertEquals("x", rep.data.active.first { it.trigger == "ri" }.id) // ayni kayit guncellendi

        val keep = ImportPlanner.plan(d, incoming, ConflictPolicy.KEEP_EXISTING, now = 50)
        assertEquals("hiperintens", keep.data.buildIndex().byTrigger["ri"]!!.replacement)
        assertEquals(1, keep.keptExisting)

        val both = ImportPlanner.plan(d, incoming, ConflictPolicy.KEEP_BOTH, now = 50)
        assertEquals(2, both.data.active.count { it.trigger == "ri" })
        assertEquals("hiperintens", both.data.buildIndex().byTrigger["ri"]!!.replacement)
        assertEquals(1, both.conflicts.size)

        // Ayni dosyayi iki kez ice aktarmak cogaltmaz
        val again = ImportPlanner.plan(rep.data, incoming, ConflictPolicy.REPLACE, now = 60)
        assertEquals(0, again.added)
        assertEquals(rep.data.active.size, again.data.active.size)
    }

    // ---------------------------------------------------------------- Espanso disa aktarma

    @Test fun `disa aktar - tekrar ice aktar ayni davranis`() {
        val recs = listOf(
            rec("ri", "hiperintens"),
            rec("drimza", "Dr. Ayşe ÖZTÜRK", mode = ExpandMode.AUTO),
            rec("rtü", "Raporda tanımlı bulgular\n\nDr. \"Ü\"\t\\son").copy(category = "GENEL"),
            rec("rw", "x").copy(wordBoundary = false),
            rec("kccc", "Karaciğer \$|\$ mm"),
            rec("pasif", "p").copy(enabled = false),
            rec("silik", "s").copy(deleted = true),
        )
        val yaml = EspansoExport.export(recs)
        val back = EspansoImport.parse("export.yml", yaml, now = 1)
        assertNull(back.error)
        val by = back.records.associateBy { it.trigger }
        assertEquals(setOf("ri", "drimza", "rtü", "rw", "kccc"), by.keys)
        for (t in by.keys) {
            val o = recs.first { it.trigger == t }
            assertEquals(o.replacement, by[t]!!.replacement, t)
            assertEquals(o.mode, by[t]!!.mode, t)
            assertEquals(o.wordBoundary, by[t]!!.wordBoundary, t)
        }
        assertEquals("GENEL", by["rtü"]!!.category)
    }
}
