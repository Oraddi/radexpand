@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.radexpand.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radexpand.core.DataOps
import com.radexpand.core.ExpandMode
import com.radexpand.core.RadExpandData
import com.radexpand.core.ShortcutRecord
import com.radexpand.core.TurkishCase
import kotlinx.coroutines.launch
import java.text.Collator

private const val F_ALL = "\u0000tum"
private const val F_OFF = "\u0000pasif"
private const val F_TRASH = "\u0000cop"
private const val F_UNUSED = "\u0000kullanilmayan"

/**
 * Kisayol listesi: arama, kategoriler, toplu islemler, cop kutusu, kullanim sayilari.
 * [usage]: kisayol (tetikleyici) -> bu cihazda kac kez genisledi (C3).
 */
@Composable
fun ShortcutsScreen(data: RadExpandData, snackbar: SnackbarHostState, store: DataStore, usage: Map<String, Int> = emptyMap()) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(F_ALL) }
    var byUsage by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var editing by remember { mutableStateOf<ShortcutRecord?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var askCategory by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val collator = remember { Collator.getInstance(TurkishCase.TR) }

    val list = remember(data, query, filter, byUsage, usage) {
        val q = TurkishCase.lower(query.trim())
        data.shortcuts.asSequence()
            .filter {
                when (filter) {
                    F_TRASH -> it.deleted
                    F_OFF -> !it.deleted && !it.enabled
                    F_ALL -> !it.deleted
                    F_UNUSED -> !it.deleted && (usage[it.trigger] ?: 0) == 0
                    else -> !it.deleted && it.category == filter
                }
            }
            .filter {
                q.isEmpty() || TurkishCase.lower(it.trigger).contains(q) ||
                    TurkishCase.lower(it.replacement).contains(q) || TurkishCase.lower(it.category).contains(q)
            }
            .sortedWith(
                if (byUsage) compareByDescending<ShortcutRecord> { usage[it.trigger] ?: 0 }.thenComparator { a, b -> collator.compare(a.trigger, b.trigger) }
                else Comparator { a, b -> collator.compare(a.trigger, b.trigger) },
            )
            .toList()
    }
    val selecting = selected.isNotEmpty()

    fun deleteSelected() {
        val ids = selected
        store.update { DataOps.delete(it, ids, now()) }
        selected = emptySet()
        scope.launch {
            val r = snackbar.showSnackbar(t("${ids.size} kısayol silindi", "${ids.size} shortcut(s) deleted"), actionLabel = t("Geri al", "Undo"), duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) store.update { DataOps.restore(it, ids, now()) }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, t("Temizle", "Clear")) } },
                placeholder = { Text(t("Ara: kısayol, metin veya kategori", "Search: shortcut, text or category")) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = {
                isNew = true
                editing = ShortcutRecord(
                    trigger = "", replacement = "",
                    category = if (filter.startsWith("\u0000")) "" else filter,
                    propagateCase = data.settings.defaultPropagateCase,
                )
            }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(t("Yeni kısayol", "New shortcut"))
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            item { FilterChip(filter == F_ALL, { filter = F_ALL; selected = emptySet() }, { Text(t("Tümü", "All") + " (${data.active.size})") }) }
            items(data.categories) { c ->
                FilterChip(filter == c, { filter = c; selected = emptySet() }, { Text("$c (${data.active.count { it.category == c }})") })
            }
            item { FilterChip(filter == F_OFF, { filter = F_OFF; selected = emptySet() }, { Text(t("Pasif", "Inactive") + " (${data.active.count { !it.enabled }})") }) }
            if (usage.isNotEmpty()) {
                item { FilterChip(filter == F_UNUSED, { filter = F_UNUSED; selected = emptySet() }, { Text(t("Hiç kullanılmayan", "Never used")) }) }
            }
            item { FilterChip(filter == F_TRASH, { filter = F_TRASH; selected = emptySet() }, { Text(t("Çöp kutusu", "Trash") + " (${data.shortcuts.count { it.deleted }})") }) }
            item { FilterChip(byUsage, { byUsage = !byUsage }, { Text(t("En çok kullanılan önce", "Most used first")) }) }
        }

        if (selecting) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("${selected.size} seçili", "${selected.size} selected"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                    TextButton(onClick = { selected = list.map { it.id }.toSet() }) { Text(t("Tümünü seç", "Select all")) }
                    Spacer(Modifier.weight(1f))
                    if (filter == F_TRASH) {
                        TextButton(onClick = { store.update { DataOps.restore(it, selected, now()) }; selected = emptySet() }) { Text(t("Geri yükle", "Restore")) }
                    } else {
                        TextButton(onClick = { store.update { DataOps.setEnabled(it, selected, true, now()) }; selected = emptySet() }) { Text(t("Aktif yap", "Activate")) }
                        TextButton(onClick = { store.update { DataOps.setEnabled(it, selected, false, now()) }; selected = emptySet() }) { Text(t("Pasif yap", "Deactivate")) }
                        TextButton(onClick = { askCategory = true }) { Text(t("Kategori…", "Category…")) }
                        TextButton(onClick = { deleteSelected() }) { Text(t("Sil", "Delete"), color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { selected = emptySet() }) { Text(t("Vazgeç", "Cancel")) }
                }
            }
        }

        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        data.active.isEmpty() && filter == F_ALL -> t(
                            "Henüz kısayol yok.\n\n\"Nasıl kullanılır\" bölümüne göz atın; \"Aktar\" bölümünden Espanso dosyanızı ya da örnek radyoloji paketini içe aktarabilir veya \"Yeni kısayol\" ile ekleyebilirsiniz.",
                            "No shortcuts yet.\n\nHave a look at \"How to use\"; you can import your Espanso file or the sample radiology pack from \"Import\", or add one with \"New shortcut\".",
                        )
                        else -> t("Bu görünümde kısayol yok.", "No shortcuts in this view.")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { r ->
                ShortcutRow(
                    r = r,
                    uses = usage[r.trigger] ?: 0,
                    selecting = selecting,
                    checked = r.id in selected,
                    onToggleSelect = { selected = if (r.id in selected) selected - r.id else selected + r.id },
                    onOpen = { isNew = false; editing = r },
                    onEnabled = { en -> store.update { DataOps.setEnabled(it, setOf(r.id), en, now()) } },
                )
                HorizontalDivider()
            }
        }
    }

    editing?.let { rec ->
        EditDialog(
            initial = rec, isNew = isNew, data = data,
            onSave = { r ->
                store.update { DataOps.upsert(it, r, now()) }
                editing = null
            },
            onDelete = {
                store.update { DataOps.delete(it, setOf(rec.id), now()) }
                editing = null
                scope.launch {
                    val res = snackbar.showSnackbar(t("«${rec.trigger}» silindi", "«${rec.trigger}» deleted"), actionLabel = t("Geri al", "Undo"))
                    if (res == SnackbarResult.ActionPerformed) store.update { DataOps.restore(it, setOf(rec.id), now()) }
                }
            },
            onDismiss = { editing = null },
        )
    }

    if (askCategory) {
        var cat by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askCategory = false },
            title = { Text(t("Kategori değiştir (${selected.size} kısayol)", "Change category (${selected.size} shortcuts)")) },
            text = {
                Column {
                    OutlinedTextField(cat, { cat = it }, label = { Text(t("Kategori", "Category")) }, singleLine = true)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                        items(data.categories) { c -> FilterChip(cat == c, { cat = c }, { Text(c) }) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.update { DataOps.setCategory(it, selected, cat.trim(), now()) }
                    selected = emptySet()
                    askCategory = false
                }) { Text(t("Uygula", "Apply")) }
            },
            dismissButton = { TextButton(onClick = { askCategory = false }) { Text(t("Vazgeç", "Cancel")) } },
        )
    }
}

@Composable
private fun ShortcutRow(
    r: ShortcutRecord,
    uses: Int,
    selecting: Boolean,
    checked: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = { if (selecting) onToggleSelect() else onOpen() }, onLongClick = onToggleSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) Checkbox(checked, { onToggleSelect() })
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.trigger, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                when (r.mode) {
                    ExpandMode.AUTO -> Badge(t("otomatik", "automatic"))
                    ExpandMode.SPECIAL_ONLY -> Badge(t("yalnızca Tab", "Tab only"))
                    ExpandMode.MANUAL -> Unit
                }
                if (!r.wordBoundary) Badge(t("kelime içinde de", "inside words too"))
                if (!r.enabled) Badge(t("pasif", "inactive"), MaterialTheme.colorScheme.errorContainer)
                if (r.category.isNotBlank()) Badge(r.category, MaterialTheme.colorScheme.surfaceVariant)
                if (uses > 0) Badge(t("$uses kez", "${uses}×"), MaterialTheme.colorScheme.tertiaryContainer)
            }
            Text(
                r.replacement.replace("\n", " ⏎ "),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!r.deleted && !selecting) Switch(r.enabled, onEnabled)
    }
}
