package com.radexpand.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SuggestionTest {
    private val learner = WordLearner()
    private fun sim(extra: List<Shortcut> = emptyList()): Sim {
        val s = Sim(listOf(Shortcut("rp", "rapor")) + extra, config = EngineConfig(suggestions = true))
        s.engine.suggester = Suggester(listOf("mutlu", "mutlaka", "mutfak"), listOf("hiperintens", "hiperintensite"), listOf("karaciğer"), learner)
        s.engine.onWordCompleted = { w -> learner.learn(w, 1) }
        return s
    }

    @Test fun `genel listeden oneri ve Tab ile kabul`() {
        var shown = emptyList<String>()
        val s = sim()
        s.engine.onSuggestions = { l, _ -> shown = l }
        s.type("Mut")
        assertEquals(listOf("Mutlu", "Mutlaka", "Mutfak"), shown)
        s.type("⇥")
        assertEquals("Mutlu", s.text)
        assertTrue(shown.isEmpty())
    }

    @Test fun `tip terimi genel listeden once gelir`() {
        val s = sim()
        var shown = emptyList<String>()
        s.engine.onSuggestions = { l, _ -> shown = l }
        s.type("bir hiper")
        assertEquals("hiperintens", shown.first())
        s.type("⇥ler")
        assertEquals("bir hiperintensler", s.text)
    }

    @Test fun `kisayol her zaman onceliklidir`() {
        val s = sim(listOf(Shortcut("mut", "mutfakta")))
        s.type("mut⇥")
        assertEquals("mutfakta", s.text)
    }

    @Test fun `kelime icinde ya da bas bilinmeden oneri yok`() {
        val s = sim()
        var shown = emptyList<String>()
        s.engine.onSuggestions = { l, _ -> shown = l }
        s.type("xmut")
        assertTrue(shown.isEmpty())
        s.type(" 3mut")
        assertTrue(shown.isEmpty())
    }

    @Test fun `ogrenilen kelime iki kez yazilinca onerilir`() {
        val s = sim()
        s.type("kolesistit ")
        var shown = emptyList<String>()
        s.engine.onSuggestions = { l, _ -> shown = l }
        s.type("koles")
        assertTrue(shown.isEmpty(), "bir kez yazilan kelime henuz onerilmez")
        s.type("istit kole")
        assertEquals(listOf("kolesistit"), shown)
    }

    @Test fun `oneri kapaliyken hicbir sey olmaz`() {
        val s = Sim(listOf(Shortcut("rp", "rapor")))
        s.engine.suggester = Suggester(listOf("mutfak"), emptyList(), emptyList(), null)
        s.type("mut⇥")
        assertEquals("mut	", s.text) // Tab programa gider
    }

    @Test fun `ogrenilenler birlestirilir ve silme tasinir`() {
        val a = listOf(LearnedWord("tomografi", 3, 10), LearnedWord("eski", 2, 5))
        val b = listOf(LearnedWord("tomografi", 5, 20), LearnedWord("yeni", 1, 30))
        val r = WordMerge.merge(a, 0, b, 8)
        val m = r.words.associateBy { it.w }
        assertEquals(5, m["tomografi"]!!.n)
        assertTrue("yeni" in m && "eski" !in m, "silmeden onceki kelime geri gelmez")
        assertEquals(8, r.clearedAt)
    }

    @Test fun `hazir listeler yuklenir`() {
        assertTrue(WordLists.turkishGeneral.size > 40_000)
        assertTrue("mutfak" in WordLists.turkishGeneral)
        assertTrue("hiperintens" in WordLists.turkishMedical)
        assertTrue(WordLists.turkishGeneral.none { it == "amk" || it.startsWith("orospu") })
    }
}
