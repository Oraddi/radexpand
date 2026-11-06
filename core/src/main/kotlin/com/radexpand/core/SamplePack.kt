package com.radexpand.core

/**
 * Ornek radyoloji kisayol paketi (C8). Kisisel bilgi icermez. Amaci, rehberdeki mantigi
 * ornekleriyle gostermek: kisa ve tahmin edilebilir kisayollar, ayni aileden ortak baslangic,
 * gercek kelimeler icin "yalnizca Tab", sablon alanlari, tarih.
 */
object SamplePack {

    private fun r(trigger: String, text: String, cat: String, mode: ExpandMode = ExpandMode.MANUAL, note: String = "") =
        ShortcutRecord(trigger = trigger, replacement = text, mode = mode, category = cat, note = note)

    fun turkish(now: Long): List<ShortcutRecord> {
        val g = "Örnek: Genel"
        val n = "Örnek: Nöro"
        val a = "Örnek: Abdomen"
        val s = "Örnek: Şablon"
        return listOf(
            // hiper = r, hipo = o; intens = i, dens = d, ekojen = e
            r("ri", "hiperintens", g, note = "hiper → r, intens → i"),
            r("oi", "hipointens", g),
            r("rd", "hiperdens", g),
            r("od", "hipodens", g),
            r("re", "hiperekojen", g),
            r("oe", "hipoekojen", g),
            r("t1r", "T1 hiperintens", n),
            r("t2r", "T2 hiperintens", n),
            r("t2f", "T2/FLAIR", n),
            r("d1", "difüzyon kısıtlayan", n),
            r("d2", "difüzyon kısıtlamayan", n),
            // ayni aile: ortak baslangic
            r("iz", "izlendi.", g, note = "iz / izş / izk ailesi"),
            r("izş", "izlenmiştir.", g),
            r("izk", "izlenmektedir.", g),
            r("mev", "mevcut", g),
            r("mevr", "mevcuttur.", g),
            // gercek kelime / birim: yalnizca Tab
            r("ön", "önerilir.", g, ExpandMode.SPECIAL_ONLY, "\"ön\" gerçek bir kelime: yalnızca Tab ile genişler"),
            r("ml", "milimetrik", g, ExpandMode.SPECIAL_ONLY, "\"ml\" bir birim: yalnızca Tab ile genişler"),
            r("kli", "Klinik korelasyon önerilir.", g),
            r("nsi", "Normal sınırlarda inceleme.", g),
            r("hm", "Hepatomegali", a),
            r("sm", "Splenomegali", a),
            // sablon: doldurma alanlari ve tarih
            r("kd", "Karaciğer kraniokaudal boyutu \$|\$ mm, dalak kraniokaudal boyutu \$|\$ mm ölçülmüştür.", s, note = "Tab ile sonraki alana geçin"),
            r("karş", "Bu tetkik \$|\$ tarihli önceki tetkik ile karşılaştırmalı değerlendirilmiştir.", s),
            r("trh", "Rapor tarihi: {tarih}", s),
            r(
                "nbt",
                "Beyin parankim sinyal intensiteleri normaldir.\nVentriküler sistem ve sisternalar normal genişliktedir.\nOrta hat yapıları santraldir.\n\nSONUÇ: Normal sınırlarda beyin MR incelemesi.",
                s,
            ),
            r("imza", "Dr. Ad SOYAD\nRadyoloji Uzmanı", s, ExpandMode.AUTO, "Otomatik: \"imza\" yazınca hemen genişler (kendi adınızla değiştirin)"),
        ).map { it.copy(updatedAt = now) }
    }

    fun english(now: Long): List<ShortcutRecord> {
        val g = "Sample: General"
        val n = "Sample: Neuro"
        val s = "Sample: Template"
        return listOf(
            r("hyi", "hyperintense", g, note = "hyper → hy, intense → i"),
            r("hoi", "hypointense", g),
            r("hyd", "hyperdense", g),
            r("hod", "hypodense", g),
            r("t1h", "T1 hyperintense", n),
            r("t2h", "T2 hyperintense", n),
            r("t2f", "T2/FLAIR", n),
            r("drs", "diffusion restricting", n),
            r("nfl", "No focal lesion is identified.", g),
            r("cc", "craniocaudal", g, ExpandMode.SPECIAL_ONLY, "\"cc\" is a unit: expands only with Tab"),
            r("cor", "Clinical correlation is recommended.", g),
            r("wnl", "within normal limits", g),
            r("lsz", "The liver measures \$|\$ cm and the spleen \$|\$ cm in craniocaudal length.", s, note = "Press Tab to go to the next field"),
            r("cmp", "Compared with the prior study dated \$|\$.", s),
            r("rdt", "Report date: {date}", s),
            r(
                "nbm",
                "Brain parenchymal signal intensity is normal.\nThe ventricles and basal cisterns are normal in size.\nNo midline shift.\n\nIMPRESSION: Normal brain MRI.",
                s,
            ),
            r("sig", "Dr. First LAST\nRadiologist", s, ExpandMode.AUTO, "Automatic: expands as soon as \"sig\" is typed (replace with your name)"),
        ).map { it.copy(updatedAt = now) }
    }

    fun forLanguage(lang: String, now: Long) = if (lang == "en") english(now) else turkish(now)
}
