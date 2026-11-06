@file:OptIn(ExperimentalMaterial3Api::class)

package com.radexpand.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.radexpand.core.ConflictPolicy
import com.radexpand.core.EspansoReport
import com.radexpand.core.ImportPlanner
import com.radexpand.core.RadExpandData

/** Espanso ice aktarma onizlemesi: hicbir sey kullanicinin onayi olmadan eklenmez. */@Composable
fun ImportPreviewDialog(reports: List<EspansoReport>, data: RadExpandData, store: DataStore, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var policy by remember { mutableStateOf(data.settings.defaultConflictPolicy) }
    val all = remember(reports) { reports.flatMap { it.records } }
    val plan = remember(policy, data, all) { ImportPlanner.plan(data, all, policy, System.currentTimeMillis()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.94f)) {
            Column(Modifier.padding(24.dp)) {
                Text(t("İçe aktarma önizlemesi", "Import preview"), style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reports.forEach { r ->
                        Text(r.fileName, fontWeight = FontWeight.Bold)
                        if (r.error != null) {
                            Text("✗ ${r.error}", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(t("${r.records.size} kısayol okunabildi · ${r.unsupported.size} aktarılamıyor · ${r.warnings.size} uyarı", "${r.records.size} shortcuts read · ${r.unsupported.size} cannot be imported · ${r.warnings.size} warnings"))
                        }
                        r.notes.forEach { Text("ℹ $it") }
                    }
                    HorizontalDivider()
                    Text(t("Sonuç: ${plan.added} yeni eklenecek · ${plan.identical} zaten aynı (çoğaltılmayacak) · ${plan.conflicts.size} çakışma", "Result: ${plan.added} new · ${plan.identical} already identical (not duplicated) · ${plan.conflicts.size} conflicts"), fontWeight = FontWeight.Bold)

                    if (plan.conflicts.isNotEmpty()) {
                        Text(t("Aynı kısayol, farklı metin — ne yapılsın?", "Same shortcut, different text — what should happen?"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(policy == ConflictPolicy.KEEP_EXISTING, { policy = ConflictPolicy.KEEP_EXISTING }, { Text(t("Mevcut kalsın", "Keep existing")) })
                            FilterChip(policy == ConflictPolicy.REPLACE, { policy = ConflictPolicy.REPLACE }, { Text(t("Gelenle değiştir", "Replace with imported")) })
                            FilterChip(policy == ConflictPolicy.KEEP_BOTH, { policy = ConflictPolicy.KEEP_BOTH }, { Text(t("İkisini de tut (gelen pasif)", "Keep both (imported inactive)")) })
                        }
                        plan.conflicts.take(60).forEach { (ex, inc) ->
                            Text("${ex.trigger}:  " + t("mevcut", "existing") + " «${ex.replacement.take(50)}»  ↔  " + t("gelen", "imported") + " «${inc.replacement.take(50)}»", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val unsupported = reports.flatMap { it.unsupported }
                    if (unsupported.isNotEmpty()) {
                        Text(t("Aktarılamayanlar", "Not imported") + " (${unsupported.size})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        unsupported.forEach { Text("• ${it.trigger}: ${it.message}", style = MaterialTheme.typography.bodySmall) }
                    }
                    val warnings = reports.flatMap { it.warnings }
                    if (warnings.isNotEmpty()) {
                        Text(t("Uyarılar (${warnings.size}) — aktarılır, kontrol etmeniz önerilir", "Warnings (${warnings.size}) — imported, please check"), fontWeight = FontWeight.Bold, color = Warn, modifier = Modifier.padding(top = 8.dp))
                        warnings.forEach { Text("• ${it.trigger}: ${it.message}", style = MaterialTheme.typography.bodySmall) }
                    }
                    val autos = all.count { it.mode == com.radexpand.core.ExpandMode.AUTO && it.note.contains("word") }
                    if (autos > 0) {
                        Hint(t("$autos kısayol Espanso'da \"word\" olmadan tanımlıydı: RadExpand'de otomatik olarak aktarılır. Bu kısayolla BAŞLAYAN bir kelime yazarsanız (ör. \"kat\" kısayolu \"katı\" yazarken) genişler; hemen Backspace ile geri alabilirsiniz. Bu davranış Ayarlar → İçe aktarma'dan değiştirilebilir.", "$autos shortcuts had no \"word\" in Espanso: they are imported as automatic. If you type a word that STARTS with such a shortcut, it expands; press Backspace right away to undo. This can be changed in Settings → Import."))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(t("Vazgeç", "Cancel")) }
                    Button(
                        enabled = all.isNotEmpty(),
                        onClick = {
                            store.backup("ice-aktarma-oncesi")
                            val p = ImportPlanner.plan(store.data.value, all, policy, System.currentTimeMillis())
                            store.update { p.data }
                            onDone(t("İçe aktarıldı: ${p.added} yeni, ${p.replaced} değiştirildi, ${p.identical} zaten vardı, ${p.keptBoth} pasif kopya.", "Imported: ${p.added} new, ${p.replaced} replaced, ${p.identical} already existed, ${p.keptBoth} inactive copies."))
                        },
                    ) { Text(t("İçe aktar", "Import")) }
                }
            }
        }
    }
}
