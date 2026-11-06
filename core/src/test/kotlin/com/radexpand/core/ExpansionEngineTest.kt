package com.radexpand.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpansionEngineTest {

    private val shortcuts = listOf(
        Shortcut("ri", "hiperintensite"),
        Shortcut("oi", "hipointens"),
        Shortcut("t2r", "T2 hiperintens"),
        Shortcut("iz", "izlendi."),
        Shortcut("izş", "izlenmiştir."),
        Shortcut("izk", "izlenmektedir."),
        Shortcut("ç", "çapında"),
        Shortcut("nsi", "Normal sınırlarda inceleme"),
        Shortcut(":adres", "Atatürk Cad. No:1"),
        Shortcut("rtü", "Raporda tanımlı bulgular\n\nDr. Ayşe ÖZTÜRK"),
        Shortcut("bel", "belirginleşme", mode = ExpandMode.SPECIAL_ONLY),
        Shortcut("drimza", "Dr. Ayşe ÖZTÜRK", mode = ExpandMode.AUTO, wordBoundary = true),
        Shortcut("ivd", "intervertebral disk", mode = ExpandMode.AUTO, wordBoundary = false),
        Shortcut("kapali", "ASLA", enabled = false),
        Shortcut("dan", "dansite"),
    )

    private fun sim(
        editor: FakeEditor = FakeEditor(),
        config: EngineConfig = EngineConfig(),
        password: Boolean = false,
        immediate: Boolean = true,
    ) = Sim(
        shortcuts, editor, config,
        FieldInfo("test.app", isPassword = password, isNullType = false, multiLine = editor.multiLine),
        reportSelectionImmediately = immediate,
    )

    private fun typed(seq: String, editor: FakeEditor = FakeEditor()) = sim(editor).type(seq).text

    // ---------------------------------------------------------------- Kabul kriterleri tablosu (bolum 12)

    @Test fun `ri tek basina genislemez`() = assertEquals("ri", typed("ri"))
    @Test fun `ri + Space`() = assertEquals("hiperintensite ", typed("ri␣"))
    @Test fun `ri + Enter`() = assertEquals("hiperintensite\n", typed("ri⏎"))
    @Test fun `ri + Tab`() = assertEquals("hiperintensite", typed("ri⇥"))
    @Test fun `ri + Tab + ler`() = assertEquals("hiperintensiteler", typed("ri⇥ler"))
    @Test fun `birinci genislemez`() = assertEquals("birinci", typed("birinci"))
    @Test fun `birinci + Space genislemez`() = assertEquals("birinci ", typed("birinci␣"))
    @Test fun `t2r + Space`() = assertEquals("T2 hiperintens ", typed("t2r␣"))
    @Test fun `otomatik kisayol son karakterde genisler`() = assertEquals("Dr. Ayşe ÖZTÜRK", typed("drimza"))

    // ---------------------------------------------------------------- Kelime siniri

    @Test fun `kelime icindeki ri genislemez`() {
        assertEquals("biri ", typed("biri␣"))
        assertEquals("biri", typed("biri⇥").trimEnd('\t'))
        assertEquals("xri\n", typed("xri⏎"))
    }

    @Test fun `ri + harf genislemez`() = assertEquals("rix ", typed("rix␣"))

    @Test fun `cumle icinde ve noktalama sonrasi genisler`() {
        assertEquals("bir hiperintensite odak", typed("bir ri␣odak"))
        assertEquals("(hiperintensite ", typed("(ri␣"))
        assertEquals("a\nhiperintensite ", typed("a⏎ri␣"))
    }

    @Test fun `Turkce karakterli kisayollar`() {
        assertEquals("izlenmiştir. ", typed("izş␣"))
        assertEquals("izlenmektedir. ", typed("izk␣"))
        assertEquals("izlendi. ", typed("iz␣"))
        assertEquals("5 mm çapında ", typed("5 mm ç␣"))
        assertEquals("aç ", typed("aç␣"))          // kelime icindeki "ç" genislemez
        assertEquals("İri ", typed("İri␣"))         // Turkce buyuk İ harf sayilir
        assertEquals("ıri ", typed("ıri␣"))
    }

    @Test fun `kesme isaretinden sonraki ek genislemez`() {
        assertEquals("Önceki MR'dan farklı", typed("Önceki MR'dan␣farklı"))
        assertEquals("T2’dan ", typed("T2’dan␣"))   // tipografik kesme
        assertEquals("dansite ", typed("dan␣"))
    }

    @Test fun `iki noktali kisayol`() {
        assertEquals("Atatürk Cad. No:1 ", typed(":adres␣"))
        assertEquals("x:adres ", typed("x:adres␣"))
    }

    // ---------------------------------------------------------------- Buyuk harf (Turkce)

    @Test fun `cumle basinda buyuk harf korunur`() {
        assertEquals("Hiperintensite ", typed("Ri␣"))
        assertEquals("İzlenmiştir. ", typed("İzş␣"))
        assertEquals("Çapında ", typed("Ç␣"))
        assertEquals("Hiperintensiteler", typed("Ri⇥ler"))
        assertEquals("T2 hiperintens ", typed("T2r␣"))
    }

    @Test fun `tamami buyuk harf yalnizca 3+ harfli kisayollarda`() {
        assertEquals("NORMAL SINIRLARDA İNCELEME ", typed("NSİ␣"))
        assertEquals("Rİ ", typed("Rİ␣"))          // 2 harf: "CC", "ML" gibi kisaltmalari korumak icin
        assertEquals("T2R ", typed("T2R␣"))
    }

    @Test fun `karisik buyuk kucuk harf genislemez`() = assertEquals("rİ ", typed("rİ␣"))

    @Test fun `noktasiz I ile yazilan buyuk RI eslesmez`() = assertEquals("RI ", typed("RI␣"))

    @Test fun `buyuk harf yayma kapali kisayol`() {
        val s = Sim(listOf(Shortcut("ri", "hiperintensite", propagateCase = false))).type("Ri␣ri␣")
        assertEquals("Ri hiperintensite ", s.text)
    }

    @Test fun `buyuk harfli genisletme geri alinir`() = assertEquals("Ri ", typed("Ri␣⌫"))

    @Test fun `otomatik kisayol buyuk harfle`() = assertEquals("DR. AYŞE ÖZTÜRK", typed("DRİMZA"))

    // ---------------------------------------------------------------- Imlec isareti ($|$)

    @Test fun `imlec isareti sablon boslugunda durur`() {
        val sc = listOf(Shortcut("kccc", "Karaciğer kraniokaudal boyutu \$|\$ mm ölçülmüştür."))
        assertEquals("Karaciğer kraniokaudal boyutu 152 mm ölçülmüştür.", Sim(sc).type("kccc␣152").text)
        assertEquals("Karaciğer kraniokaudal boyutu 152 mm ölçülmüştür.", Sim(sc).type("kccc⇥152").text)
        assertEquals("Karaciğer kraniokaudal boyutu 152 mm ölçülmüştür.", Sim(sc).type("kccc⏎152").text)
    }

    @Test fun `imlec isareti sonrasi Backspace normal siler`() {
        val sc = listOf(Shortcut("x1", "A \$|\$ B"))
        assertEquals("A  B", Sim(sc).type("x1␣5⌫").text)
    }

    // ---------------------------------------------------------------- Noktalama ile genisletme

    @Test fun `noktalama ile genisletme varsayilan kapali`() = assertEquals("ri, ", typed("ri,␣"))

    @Test fun `noktalama ile genisletme acikken`() {
        val s = sim(config = EngineConfig(punctuationTriggers = true)).type("bir ri, t2r.")
        assertEquals("bir hiperintensite, T2 hiperintens.", s.text)
        val u = sim(config = EngineConfig(punctuationTriggers = true)).type("ri,⌫")
        assertEquals("ri,", u.text)
        val w = sim(config = EngineConfig(punctuationTriggers = true)).type("biri,")
        assertEquals("biri,", w.text)
    }

    // ---------------------------------------------------------------- Uygulama bazinda ayarlar

    @Test fun `kapatilan uygulamada genisletme yok`() {
        val s = sim(config = EngineConfig(disabledPackages = setOf("test.app"))).type("ri␣drimza")
        assertEquals("ri drimza", s.text)
    }

    @Test fun `kor mod zorlanan uygulamada editore hic sorulmaz`() {
        val ed = FakeEditor()
        val s = sim(editor = ed, config = EngineConfig(blindPackages = setOf("test.app"))).type("ri␣bir ri⇥")
        assertEquals("hiperintensite bir hiperintensite", s.text)
        assertEquals(0, ed.icCalls)
    }

    @Test fun `gozlemci olaylari metin icermez`() {
        val events = ArrayList<String>()
        val s = sim()
        s.engine.observer = object : EngineObserver {
            override fun onLookup(packageName: String, millis: Long, editorGaveText: Boolean) { events += "L:$packageName:$editorGaveText" }
            override fun onExpansion(packageName: String, trigger: String) { events += "E:$trigger" }
            override fun onUndo(packageName: String, trigger: String) { events += "U:$trigger" }
        }
        s.type("gizli ri␣⌫")
        assertTrue("E:ri" in events && "U:ri" in events && events.any { it.startsWith("L:test.app:true") }, events.toString())
        assertTrue(events.none { "gizli" in it })
    }

    @Test fun `pasif kisayol genislemez`() = assertEquals("kapali ", typed("kapali␣"))

    // ---------------------------------------------------------------- Modlar

    @Test fun `sadece-ozel-tus kisayolu Space ve Enter ile genislemez, Tab ile genisler`() {
        assertEquals("bel ", typed("bel␣"))
        assertEquals("bel\n", typed("bel⏎"))
        assertEquals("belirginleşme", typed("bel⇥"))
        assertEquals("belirginleşmeler", typed("bel⇥ler"))
    }

    @Test fun `otomatik + kelime siniri acik ise kelime icinde genislemez`() =
        assertEquals("adrimza", typed("adrimza"))

    @Test fun `otomatik + kelime siniri kapali ise kelime icinde de genisler`() =
        assertEquals("xintervertebral disk", typed("xivd"))

    @Test fun `otomatik kisayol Space beklemez, sonrasi normal yazilir`() =
        assertEquals("Dr. Ayşe ÖZTÜRK ve", typed("drimza␣ve"))

    @Test fun `cok satirli metin`() =
        assertEquals("Raporda tanımlı bulgular\n\nDr. Ayşe ÖZTÜRK ", typed("rtü␣"))

    @Test fun `eslesme yoksa Space Enter Tab normal calisir`() {
        assertEquals("abc def", typed("abc␣def"))
        assertEquals("abc\ndef", typed("abc⏎def"))
        assertEquals("abc\tdef", typed("abc⇥def"))
    }

    @Test fun `tek satirli alanda Enter genisletir ve eylemi tetikler`() {
        val ed = FakeEditor(multiLine = false)
        val s = sim(ed).type("ri⏎")
        assertEquals("hiperintensite", s.text)
        assertEquals(1, ed.editorActions)
    }

    @Test fun `art arda hizli genisletmeler`() =
        assertEquals("hiperintensite hiperintensite hiperintensite hipointens ", typed("ri␣ri␣ri␣oi␣"))

    // ---------------------------------------------------------------- Geri alma (Backspace)

    @Test fun `Space genisletmesinden hemen sonra Backspace geri alir`() = assertEquals("ri ", typed("ri␣⌫"))
    @Test fun `Tab genisletmesinden sonra Backspace geri alir`() = assertEquals("ri", typed("ri⇥⌫"))
    @Test fun `otomatik genisletmeden sonra Backspace geri alir`() = assertEquals("drimza", typed("drimza⌫"))
    @Test fun `ikinci Backspace normal siler`() = assertEquals("ri", typed("ri␣⌫⌫"))
    @Test fun `araya tus girerse Backspace normal siler`() = assertEquals("hiperintensite ", typed("ri␣x⌫"))

    @Test fun `imlec baska yere tasinirsa geri alma iptal olur`() {
        val s = sim()
        s.type("ri␣")
        s.clickAt(3) // fareyle metnin icine tiklandi
        s.type("⌫")
        assertEquals("hierintensite ", s.text)
    }

    @Test fun `geri alinan kisayol tekrar genisletilebilir`() = assertEquals("hiperintensite", typed("ri␣⌫⌫⇥"))

    // ---------------------------------------------------------------- Guvenlik

    @Test fun `parola alaninda genisletme yok`() {
        val s = sim(password = true).type("ri␣drimza")
        assertEquals("ri drimza", s.text)
    }

    @Test fun `kapaliyken genisletme yok`() {
        val s = sim(config = EngineConfig(enabled = false)).type("ri␣")
        assertEquals("ri ", s.text)
    }

    @Test fun `imlec bir kelimenin ortasina tasinirsa sinir editor metninden denetlenir`() {
        val s = sim()
        s.type("bi␣nci")
        s.clickAt(2) // "bi|"
        s.type("ri␣")
        assertEquals("biri  nci", s.text)
    }

    @Test fun `tanilama kaydinda yazilan metin yer almaz`() {
        val s = sim().type("gizli hasta adı ri␣")
        val all = s.log.joinToString("\n")
        assertTrue("gizli" !in all && "hasta" !in all, all)
        assertTrue("hiperintensite" !in all, all)
    }

    // ---------------------------------------------------------------- Kor mod (editor metin vermiyor)

    @Test fun `kor mod - alan basinda genisler`() {
        val s = sim(editor = FakeEditor(supportsText = false)).type("ri␣")
        assertEquals("hiperintensite ", s.text)
    }

    @Test fun `kor mod - imlec tasindiktan sonra sol taraf bilinmiyorsa genisletmez`() {
        val ed = FakeEditor(supportsText = false)
        val s = sim(editor = ed)
        s.type("bi␣nci")
        s.clickAt(2)
        s.type("ri␣")
        assertEquals("biri  nci", s.text)
    }

    @Test fun `kor mod - bosluktan sonra yazilan kisayol genisler`() {
        val ed = FakeEditor(supportsText = false)
        val s = sim(editor = ed)
        s.type("abc")
        s.clickAt(1)
        s.clickAt(3) // imlec oynadi: tampon artik guvenilir degil
        s.type("␣ri␣")
        assertEquals("abc hiperintensite ", s.text)
    }

    @Test fun `Windows kipi - tiklamadan sonra ilk kelime kelime basi sayilir`() {
        val cfg = EngineConfig(unknownStartIsBoundary = true)
        val s = sim(editor = FakeEditor(supportsText = false), config = cfg)
        s.type("abc")
        s.clickAt(1)
        s.clickAt(3)
        s.type("ri␣")
        assertEquals("abchiperintensite ", s.text) // bilinen ve kabul edilen risk (Espanso ile ayni)
        val t = sim(editor = FakeEditor(supportsText = false), config = cfg)
        t.engine.onExternalCursorMove()
        t.type("ri⇥ler biri␣")
        assertEquals("hiperintensiteler biri ", t.text)
    }

    @Test fun `kor mod kapatilabilir`() {
        val s = sim(editor = FakeEditor(supportsText = false), config = EngineConfig(allowBlindMode = false)).type("ri␣")
        assertEquals("ri ", s.text)
    }

    // ---------------------------------------------------------------- Yavas editor

    @Test fun `yavas editor bir kez beklenir, sonra tamponla devam edilir`() {
        var now = 0L
        val slow = object : FakeEditor() {
            override fun textBeforeCursor(n: Int): CharSequence? {
                now += 2_000_000_000L // her sorgu 2 sn suruyor
                return super.textBeforeCursor(n)
            }
        }
        val s = Sim(shortcuts, slow, clock = { now })
        s.type("ri␣bir ri⇥ler t2r␣")
        assertEquals("hiperintensite bir hiperintensiteler T2 hiperintens ", s.text)
        assertEquals(1, slow.icCalls, "yavaş editöre yalnızca bir kez sorulmalı")
        s.type("ri␣⌫") // geri alma tamponla dogrulanir
        assertEquals("hiperintensite bir hiperintensiteler T2 hiperintens ri ", s.text)
        assertTrue(s.log.any { "yavaş" in it })
    }

    // ---------------------------------------------------------------- Asenkron editor (Chrome benzeri)

    @Test fun `imlec bildirimleri gecikse de dogru calisir`() {
        val s = sim(immediate = false)
        s.type("ri␣bir ri⇥ler t2r⏎")
        s.flushSelection()
        assertEquals("hiperintensite bir hiperintensiteler T2 hiperintens\n", s.text)
        s.type("ri␣⌫")
        s.flushSelection()
        assertEquals("hiperintensite bir hiperintensiteler T2 hiperintens\nri ", s.text)
    }

    // ---------------------------------------------------------------- Dizin

    @Test fun `tekrarlanan tetikleyiciler raporlanir`() {
        val idx = ShortcutIndex(listOf(Shortcut("havak", "a"), Shortcut("havak", "a"), Shortcut("x", "y")))
        assertEquals(listOf("havak"), idx.duplicates)
        assertEquals(2, idx.size)
    }

    @Test fun `NFD yazilmis Turkce tetikleyici NFC'ye cevrilir`() {
        val nfd = "izş" // s + birlesik cengel = ş
        val idx = ShortcutIndex(listOf(Shortcut(nfd, "izlenmiştir.")))
        assertTrue(idx.byTrigger.containsKey("izş"))
    }

    // ---------------------------------------------------------------- Performans

    @Test fun `binlerce kisayolla hizli yazim`() {
        val many = (0 until 3000).map { Shortcut("k$it", "metin $it") } + shortcuts
        val s = Sim(many)
        val word = "birinci ikinci ri⇥ler t2r␣ "
        val t0 = System.nanoTime()
        repeat(300) { s.type(word) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(s.text.contains("hiperintensiteler T2 hiperintens"))
        assertTrue(ms < 2000, "çok yavaş: $ms ms")
    }
}
