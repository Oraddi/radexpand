package com.radexpand.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ilk acilis sihirbazi (B2): 1) dil, 2) platform kurulumu (klavye secimi / Windows baslangic),
 * 3) baslangic secimi (ornek paket, Espanso dosyasi, bos).
 */
@Composable
fun Onboarding(
    store: DataStore,
    /** 2. adim: platforma ozgu kurulum (Android: klavyeyi etkinlestir/sec). */
    setupStep: @Composable () -> Unit,
    onImportSample: () -> Unit,
    onImportEspanso: () -> Unit,
    onFinish: () -> Unit,
) {
    var step by remember { mutableStateOf(if (store.data.value.settings.language.isEmpty()) 0 else 1) }
    fun finish() {
        store.update { it.copy(settings = it.settings.copy(onboardingDone = true)) }
        onFinish()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("RadExpand", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            when (step) {
                0 -> {
                    Text("Dil seçin / Choose language", style = MaterialTheme.typography.headlineSmall)
                    Text("Daha sonra Ayarlar'dan değiştirebilirsiniz. / You can change it later in Settings.")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { UiLang.lang = "tr"; store.update { it.copy(settings = it.settings.copy(language = "tr")) }; step = 1 }) { Text("Türkçe", fontSize = 18.sp) }
                        Button(onClick = { UiLang.lang = "en"; store.update { it.copy(settings = it.settings.copy(language = "en")) }; step = 1 }) { Text("English", fontSize = 18.sp) }
                    }
                }
                1 -> {
                    Text(t("1 / 2 · Kurulum", "1 / 2 · Setup"), style = MaterialTheme.typography.headlineSmall)
                    setupStep()
                    Row {
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { step = 2 }) { Text(t("Devam", "Continue")) }
                    }
                }
                else -> {
                    Text(t("2 / 2 · Nasıl başlamak istersiniz?", "2 / 2 · How do you want to start?"), style = MaterialTheme.typography.headlineSmall)
                    Text(t("Her zaman \"Nasıl kullanılır\" bölümünden başlayabilirsiniz; kısa ve sadedir.", "You can always start with \"How to use\"; it is short and simple."))
                    Button(onClick = { onImportSample(); finish() }, modifier = Modifier.fillMaxWidth()) { Text(t("Örnek radyoloji paketiyle başla", "Start with the sample radiology pack")) }
                    OutlinedButton(onClick = { finish(); onImportEspanso() }, modifier = Modifier.fillMaxWidth()) { Text(t("Espanso dosyamı içe aktar", "Import my Espanso file")) }
                    TextButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text(t("Boş başla", "Start empty")) }
                    Row {
                        TextButton(onClick = { step = 1 }) { Text(t("Geri", "Back")) }
                        Spacer(Modifier.width(1.dp))
                    }
                }
            }
            Text(
                t("RadExpand bir tıbbi cihaz değildir; oluşan metni kontrol etmek kullanıcının sorumluluğundadır.", "RadExpand is not a medical device; checking the resulting text is the user's responsibility."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Ornek paketi mevcut verinin uzerine (mevcut kisayollari ezmeden) ekler; once yedek alir. */
fun importSamplePack(store: DataStore): Int {
    store.backup("ornek-paket-oncesi")
    val recs = com.radexpand.core.SamplePack.forLanguage(UiLang.lang, now())
    val p = com.radexpand.core.ImportPlanner.plan(store.data.value, recs, com.radexpand.core.ConflictPolicy.KEEP_EXISTING, now())
    store.update { p.data }
    return p.added
}
