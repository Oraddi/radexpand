package com.radexpand.core

/**
 * Gunluk dilde gercek kelime ya da yaygin birim/kisaltma olan kisa sozcukler (C4).
 * Bir kisayol bunlardan biriyse, Space ile istemeden genisleyebilir: kullaniciya uyari gosterilir
 * ve "Yalnizca Tab" modu onerilir. Liste bilerek kisa tutuldu (en sik ve en kisa olanlar).
 */
object CommonWords {
    private val turkish = """
        ve ile de da ki mi mı mu mü ne o bu şu bir iki üç dört beş altı yedi sekiz dokuz on yüz bin
        ben sen biz siz onlar var yok çok az en daha gibi için kadar sonra önce ön arka üst alt iç dış orta yan
        sağ sol düz eğri ince kalın uzun kısa büyük küçük yeni eski açık kapalı tam yarım her hiç bazı tüm
        al ver gel git bak gör bil yap et ol kal dur çık gir aç kapa yaz oku say iz bel boy baş göz el ayak kol diz
        kan can su taş yağ kas kemik deri yüz ağız dil diş boğaz göğüs karın bel kalça bacak kafa beyin kalp akciğer
        ana ata eş iş aş at ay ad af ak ar as aş ek el em en er es ev ez ıl in it iz ok ol on or ot öç öd ön ör öz
        ul un us ut uz üç ün üs üt üz yan yar yaş yat yay yaz yel yem yer yol yön yük yün yüz
        kor med sin min mak dan den ten tan sek sert yumuşak hafif ağır sıcak soğuk hızlı yavaş ilk son
        normal sınır ana dal kök uç koç doz kez kat sıra çift tek yak ant uy
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

    private val english = """
        a an and are as at be by do go he if in is it me my no of on or so to up us we
        all any are bad big but can day did end for get had has her him his how its let man new not now old one our out
        own put say see she the too two use was way who why yes yet you
        left right mid low high top base mass size wall rim tip
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

    /** Olcu birimleri ve klinik kisaltmalar: rakamdan sonra sik yazilir ("500 cc", "5 mm"). */
    private val units = setOf(
        "cc", "ml", "mm", "cm", "kg", "mg", "gr", "lt", "dk", "sn", "sa", "hu", "ct", "mr", "us", "bt", "pa", "ap", "iv", "im",
        "mlo", "cc", "dwi", "adc", "t1", "t2", "kv", "mas", "msv",
    )

    enum class Kind { WORD, UNIT }

    /** Kisayol gunluk bir kelime ya da birimse turunu dondurur; degilse null. */
    fun check(trigger: String): Kind? {
        val t = TurkishCase.lower(trigger.trim())
        if (t in units) return Kind.UNIT
        if (t in turkish || t in english) return Kind.WORD
        return null
    }
}
