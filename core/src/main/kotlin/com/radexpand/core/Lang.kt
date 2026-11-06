package com.radexpand.core

import java.util.Locale

/**
 * Arayuz dili (Turkce / Ingilizce). Metinler kullanildigi yerde iki dilli yazilir: tx("Kaydet", "Save").
 * Boylece hicbir metnin cevirisi unutulmaz. Arayuz katmani dil degisince [lang]'i gunceller.
 */
object Lang {
    @Volatile
    var lang: String = "tr"

    val isEnglish: Boolean get() = lang == "en"

    /** Ayarda dil secilmemisse cihaz diline gore varsayilan. */
    fun defaultFor(setting: String): String =
        setting.ifEmpty { if (Locale.getDefault().language == "tr") "tr" else "en" }
}

/** Gecerli dile gore metin. */
fun tx(tr: String, en: String): String = if (Lang.isEnglish) en else tr
