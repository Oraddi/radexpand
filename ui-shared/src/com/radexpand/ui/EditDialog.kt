@file:OptIn(ExperimentalMaterial3Api::class)

package com.radexpand.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.radexpand.core.DataOps
import com.radexpand.core.ExpandMode
import com.radexpand.core.ExpansionEngine
import com.radexpand.core.RadExpandData
import com.radexpand.core.ShortcutRecord

@Composable
fun EditDialog(
    initial: ShortcutRecord,
    isNew: Boolean,
    data: RadExpandData,
    onSave: (ShortcutRecord) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var trigger by remember(initial.id) { mutableStateOf(initial.trigger) }
    var repl by remember(initial.id) { mutableStateOf(TextFieldValue(initial.replacement)) }
    var mode by remember(initial.id) { mutableStateOf(initial.mode) }
    var boundary by remember(initial.id) { mutableStateOf(initial.wordBoundary) }
    var case by remember(initial.id) { mutableStateOf(initial.propagateCase) }
    var enabled by remember(initial.id) { mutableStateOf(initial.enabled) }
    var category by remember(initial.id) { mutableStateOf(initial.category) }

    val draft = initial.copy(
        trigger = trigger.trim(), replacement = repl.text, mode = mode, wordBoundary = boundary,
        propagateCase = case, enabled = enabled, category = category.trim(), deleted = false,
    )
    val problems = DataOps.validateDetailed(data, draft)
    val blocked = problems.any { it.blocking }

    fun insertAtCursor(s: String) {
        val at = repl.selection.start.coerceIn(0, repl.text.length)
        repl = TextFieldValue(repl.text.substring(0, at) + s + repl.text.substring(at), TextRange(at + s.length))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.94f)) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (isNew) t("Yeni kısayol", "New shortcut") else t("Kısayolu düzenle", "Edit shortcut"), style = MaterialTheme.typography.headlineSmall)
                if (initial.note.isNotBlank()) Hint(t("Not: ", "Note: ") + initial.note)

                OutlinedTextField(
                    trigger, { trigger = it },
                    label = { Text(t("Kısayol (ör. ri)", "Shortcut (e.g. ri)")) }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    repl, { repl = it },
                    label = { Text(t("Genişletilecek metin", "Replacement text")) },
                    minLines = 3, maxLines = 12,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { insertAtCursor(ExpansionEngine.CURSOR) }) { Text(t("Doldurma alanı ekle (\$|\$)", "Add fill-in field (\$|\$)")) }
                    OutlinedButton(onClick = { insertAtCursor(t("{tarih}", "{date}")) }) { Text(t("Tarih ekle", "Add date")) }
                    OutlinedButton(onClick = { insertAtCursor(t("{saat}", "{time}")) }) { Text(t("Saat ekle", "Add time")) }
                }
                Hint(
                    t(
                        "\$|\$: genişletmeden sonra imleç ilk işarette durur; Tab ile sıradaki işarete geçilir. Örnek: \"Karaciğer \$|\$ mm, dalak \$|\$ mm\". {tarih} ve {saat} o anki tarih ve saatle değişir.",
                        "\$|\$: after expansion the cursor stops at the first mark; Tab moves to the next one. Example: \"Liver \$|\$ mm, spleen \$|\$ mm\". {date} and {time} are replaced with the current date and time.",
                    ),
                )

                Text(t("Ne zaman genişlesin?", "When should it expand?"), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(mode == ExpandMode.MANUAL, { mode = ExpandMode.MANUAL }, { Text(t("Space / Enter / Tab ile", "With Space / Enter / Tab")) })
                    FilterChip(mode == ExpandMode.AUTO, { mode = ExpandMode.AUTO }, { Text(t("Otomatik (son harfte)", "Automatic (on last letter)")) })
                    FilterChip(mode == ExpandMode.SPECIAL_ONLY, { mode = ExpandMode.SPECIAL_ONLY }, { Text(t("Yalnızca Tab ile", "Tab only")) })
                }
                Hint(
                    when (mode) {
                        ExpandMode.MANUAL -> t(
                            "Space: metin + boşluk. Enter: metin + yeni satır. Tab: yalnızca metin, imleç hemen sonda (ek yazmak için: ri + Tab + ler). Tuş davranışları Ayarlar'dan değiştirilebilir.",
                            "Space: text + space. Enter: text + new line. Tab: text only, cursor right after it (to add a suffix). Key behaviour can be changed in Settings.",
                        )
                        ExpandMode.AUTO -> t(
                            "Kısayolun son harfi yazılır yazılmaz genişler. Başka bir kelimenin başı olabilecek kısayollarda dikkat.",
                            "Expands as soon as the last letter is typed. Be careful with shortcuts that can be the start of another word.",
                        )
                        ExpandMode.SPECIAL_ONLY -> t(
                            "Space ve Enter ile genişlemez; yalnızca Tab (ya da atadığınız özel tuş) ile. Gerçek bir kelimeye denk gelen kısayollar için (ör. \"ön\", \"düz\", \"cc\").",
                            "Does not expand with Space or Enter; only with Tab (or your special key). For shortcuts that are real words.",
                        )
                    },
                )

                SwitchRow(
                    t("Kelime sınırı", "Word boundary"),
                    t("Açıkken kısayolun solunda harf/rakam olamaz: \"birinci\" içindeki \"ri\" genişlemez.", "When on, there can be no letter/digit before the shortcut: \"ri\" inside \"Ferris\" does not expand."),
                    boundary,
                ) { boundary = it }
                SwitchRow(
                    t("Büyük harfi koru", "Keep capitalization"),
                    t("Ri → Hiperintens, İzş → İzlenmiştir.", "Ri → Hyperintense, NSI → NORMAL STUDY."),
                    case,
                ) { case = it }
                SwitchRow(t("Aktif", "Active"), t("Pasif kısayollar saklanır ama genişlemez.", "Inactive shortcuts are kept but do not expand."), enabled) { enabled = it }

                OutlinedTextField(category, { category = it }, label = { Text(t("Kategori", "Category")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (data.categories.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(data.categories) { c -> FilterChip(category == c, { category = c }, { Text(c) }) }
                    }
                }

                problems.forEach { p ->
                    Text((if (p.blocking) "✗ " else "⚠ ") + p.message, color = if (p.blocking) MaterialTheme.colorScheme.error else Warn)
                }

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!isNew) TextButton(onClick = onDelete) { Text(t("Sil", "Delete"), color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(t("Vazgeç", "Cancel")) }
                    Button(onClick = { onSave(draft) }, enabled = !blocked) { Text(t("Kaydet", "Save")) }
                }
            }
        }
    }
}
