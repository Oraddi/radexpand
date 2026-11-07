@file:OptIn(ExperimentalLayoutApi::class)

package com.radexpand.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Nasil kullanilir" rehberi (B1). Amac: ornek ezberletmek degil, programin MANTIGINI anlatmak.
 * Sade kartlar; her kartta 2-3 cumle ve bir "yazdiginiz -> olan" ornegi.
 */
@Composable
fun GuideScreen(isWindows: Boolean, onTryIt: () -> Unit, onImportSample: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t("Nasıl kullanılır", "How to use"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
        Hint(t("Beş dakikada programın mantığı. Kartlar sırayla okunacak şekilde yazıldı.", "The logic of the program in five minutes. Read the cards in order."))

        GuideCard(
            "1",
            t("Kısayol nedir?", "What is a shortcut?"),
            t(
                "Kısa bir harf grubu yazıp bir tuşa basarsınız, uzun metne dönüşür. Klavyeniz değişmez: harfler her zaman olduğu gibi yazılır; RadExpand yalnızca tanıdığı bir kısayolu görünce onu metinle değiştirir.",
                "You type a few letters and press a key; they turn into a longer text. Your keyboard does not change: letters are typed as usual; RadExpand only replaces a shortcut it recognises.",
            ),
            listOf(t("rp ␣", "rpt ␣") to t("rapor·", "report·")),
        )
        GuideCard(
            "2",
            t("Ne zaman genişler?", "When does it expand?"),
            t(
                "Genişletme, kısayoldan sonra bastığınız tuşla olur. Space metnin arkasına boşluk koyar, Enter yeni satıra geçer, Tab hiçbir şey eklemez: imleç metnin hemen sonunda kalır ve ek yazabilirsiniz. Tuşların davranışı Ayarlar'dan değiştirilebilir.",
                "Expansion happens with the key you press after the shortcut. Space adds a space, Enter starts a new line, Tab adds nothing: the cursor stays right after the text so you can add a suffix. Key behaviour can be changed in Settings.",
            ),
            listOf(
                t("rp ␣", "rpt ␣") to t("rapor·", "report·"),
                t("rp ⏎", "rpt ⏎") to t("rapor⏎", "report⏎"),
                t("rp ⇥ lar", "rpt ⇥ s") to t("raporlar", "reports"),
            ),
        )
        GuideCard(
            "3",
            t("Üç çalışma şekli", "Three modes"),
            t(
                "Her kısayol için seçilir. Space/Enter/Tab ile (varsayılan, en güvenlisi). Otomatik: son harf yazılınca hemen genişler — yalnızca uzun ve başka kelimeye benzemeyen kısayollarda kullanın. Yalnızca Tab: Space ile asla genişlemez; gerçek kelimeye denk gelen kısayollar için.",
                "Chosen per shortcut. With Space/Enter/Tab (default, safest). Automatic: expands as soon as the last letter is typed — use only for long shortcuts that look like no other word. Tab only: never expands with Space; for shortcuts that are real words.",
            ),
            listOf(t("imza", "sig") to t("Dr. Ad SOYAD (otomatik)", "Dr. First LAST (automatic)"), t("ön ␣ / ön ⇥", "cc ␣ / cc ⇥") to t("ön·  /  önerilir.", "cc·  /  craniocaudal")),
        )
        GuideCard(
            "4",
            t("Kelime sınırı: yanlışlıkla genişlemez", "Word boundary: no accidental expansion"),
            t(
                "Kısayol ancak ayrı bir kelime olarak yazıldığında genişler. Böylece \"turp\" yazarken sonundaki \"rp\" genişlemez. Kesme işaretinden sonraki ek de korunur (\"Ankara'dan\"). Bir şey yanlışlıkla genişlerse hemen Backspace'e basın: yazdığınız geri gelir.",
                "A shortcut expands only at the start of a separate word, so typing \"excerpt\" does not expand the \"rpt\" at its end. If something expands by mistake, press Backspace right away: what you typed comes back.",
            ),
            listOf(t("turp ␣", "excerpt ␣") to t("turp· (değişmez)", "excerpt· (unchanged)"), t("rp ␣ ⌫", "rpt ␣ ⌫") to t("rp·", "rpt·")),
        )
        GuideCard(
            "5",
            t("İyi kısayol nasıl seçilir?", "Choosing good shortcuts"),
            t(
                "Kısa ama tahmin edilebilir olsun: kelimenin ünsüzleri iyi bir başlangıçtır (rapor → rp, toplantı → tpl). Aynı ailedekiler ortak başlasın: iz → izlendi, izş → izlenmiştir, izk → izlenmektedir. Gerçek kelimelerden ve birimlerden (ön, düz, cc, ml) kaçının; kullanırsanız \"Yalnızca Tab\" yapın — RadExpand bu durumda sizi uyarır.",
                "Short but predictable: a word's consonants are a good start (report → rpt, meeting → mtg). Members of a family share a start. Avoid real words and units (cc, ml); if you use them, set them to \"Tab only\" — RadExpand warns you.",
            ),
            emptyList(),
        )
        GuideCard(
            "6",
            t("Büyük harf", "Capitals"),
            t(
                "Cümle başında kısayolu büyük harfle yazın, metin de büyük harfle başlar. Tamamı büyük yazım yalnızca uzun kısayollarda çalışır; \"CC\" gibi kısaltmalar bozulmaz. İsterseniz Ayarlar'dan metnin olduğu gibi yazılmasını ya da büyük harfle yazılan kısayolun hiç genişlememesini seçebilirsiniz.",
                "Type the shortcut with a capital at the start of a sentence and the text starts with a capital. All-caps works only for longer shortcuts, so abbreviations like \"CC\" stay as they are. In Settings you can instead keep the text as saved, or not expand capitalised shortcuts at all.",
            ),
            listOf(t("Rp ␣", "Rpt ␣") to t("Rapor·", "Report·"), "CC ␣" to "CC·"),
        )
        GuideCard(
            "7",
            t("Şablonlar ve doldurma alanları", "Templates and fill-in fields"),
            t(
                "Metne \$|\$ koyduğunuz yerler doldurma alanıdır: genişletmeden sonra imleç ilk alanda durur, Tab ile sıradakine geçersiniz. {tarih} ve {saat} o anki tarih ve saatle değişir.",
                "The places where you put \$|\$ are fill-in fields: after expansion the cursor stops at the first one and Tab moves to the next. {date} and {time} become the current date and time.",
            ),
            listOf(t("tpl ␣ 12 Mart ⇥ 3", "mtg ␣ 12 March ⇥ 3") to t("Toplantı 12 Mart tarihinde, 3 salonunda …", "The meeting will be held on 12 March in room 3.")),
        )
        GuideCard(
            "8",
            t("Espanso'dan geçiş ve iki cihaz", "Coming from Espanso, two devices"),
            t(
                "\"Aktar\" bölümünden Espanso dosyanızı (base.yml) seçin; önce bir önizleme görürsünüz, hiçbir şey onayınız olmadan eklenmez. Tablet ve bilgisayarda aynı listeyi kullanmak için iki tarafta da aynı senkron dosyasını (ör. Google Drive klasöründe) seçin.",
                "Pick your Espanso file (base.yml) in \"Import\"; you see a preview first and nothing is added without your approval. To use the same list on tablet and computer, choose the same sync file (e.g. in a Google Drive folder) on both.",
            ),
            emptyList(),
        )
        GuideCard(
            "9",
            t("Sık sorunlar", "Common problems"),
            if (isWindows) t(
                "Hiç genişlemiyor: tepsideki simge duraklatılmış olabilir, ya da program yönetici olarak çalışıyordur (RadExpand'i de yönetici olarak açın). İki kez genişliyor: Espanso da açık — kapatın. Bir programda istemiyorsanız: Ayarlar → Uygulama bazında kapatma.",
                "Nothing expands: the tray icon may be paused, or the program runs as administrator (run RadExpand as administrator too). Expands twice: Espanso is also running — close it. Don't want it in a program: Settings → Per-app.",
            ) else t(
                "Hiç genişlemiyor: klavye olarak RadExpand seçili mi? (Ayarlar'ın üstü). Türkçe harfler yanlış: Ayarlar → Fiziksel klavye düzeni → Türkçe Q. Bir uygulamada sorun var: Uyumluluk bölümü o uygulamada ne olduğunu gösterir; oradan kapatabilirsiniz.",
                "Nothing expands: is RadExpand selected as the keyboard? (top of Settings). Wrong letters: Settings → Physical keyboard layout. Trouble in one app: the Compatibility section shows what happens there; you can turn it off for that app.",
            ),
            emptyList(),
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("Şimdi deneyin", "Try it now"), fontWeight = FontWeight.Bold)
                Hint(t("Örnek paket yukarıdaki örneklerin hepsini içerir. İstemediğinizde \"Örnek:\" kategorisini silebilirsiniz.", "The sample pack contains all the examples above. Delete the \"Sample:\" categories when you no longer need them."))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onImportSample) { Text(t("Örnek paketi içe aktar", "Import sample pack")) }
                    OutlinedButton(onClick = onTryIt) { Text(t("Deneme alanını aç", "Open the practice area")) }
                }
            }
        }
        Hint(
            t(
                "RadExpand bir tıbbi cihaz değildir; oluşan metni kontrol etmek kullanıcının sorumluluğundadır.",
                "RadExpand is not a medical device; checking the resulting text is the user's responsibility.",
            ),
        )
        Spacer(Modifier.padding(24.dp))
    }
}

@Composable
private fun GuideCard(no: String, title: String, body: String, examples: List<Pair<String, String>>) {
    Card(Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                    Text(no, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
            }
            Text(body)
            if (examples.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    examples.forEach { (typed, result) ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(typed, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text("  →  ", fontSize = 14.sp)
                                Text(result, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}
