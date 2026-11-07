package com.radexpand.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.radexpand.core.KnownApps

/**
 * Uygulama bazinda kapatma: hazir yaygin uygulamalar + son kullanilanlar + zaten kapatilmis olanlar.
 * [known]: gosterilecek hazir uygulamalar (Android'de yalnizca yuklu olanlar).
 * [seen]: son kullanilan uygulamalarin kimlikleri; [labelOf]: kimlikten okunur ad.
 */
@Composable
fun PerAppSection(
    known: List<KnownApps.App>,
    seen: List<String>,
    disabled: Set<String>,
    labelOf: (String) -> String,
    onToggle: (id: String, off: Boolean) -> Unit,
) {
    SectionTitle(t("Uygulama bazında kapatma", "Turn off per app"))
    Hint(
        t(
            "RadExpand'in çalışmasını istemediğiniz uygulamaları kapatın (ör. parola yöneticisi, oyunlar ya da kendi kısayolları olan bir program). Kapalı uygulamada tuşlar olduğu gibi gider.",
            "Turn RadExpand off in apps where you don't want it (e.g. a password manager, games, or a program with its own shortcuts). In a turned-off app, keys pass through unchanged.",
        ),
    )
    val knownIds = known.map { it.id }.toSet()
    Column {
        if (known.isNotEmpty()) Text(t("Yaygın uygulamalar", "Common apps"), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        known.forEach { app ->
            SwitchRow(app.name, if (app.id in disabled) t("KAPALI · ", "OFF · ") + app.id else app.id, app.id in disabled) { off -> onToggle(app.id, off) }
        }
        val others = (seen + disabled).distinct().filter { it !in knownIds }
        if (others.isNotEmpty()) Text(t("Son kullandıklarınız ve diğerleri", "Recently used and others"), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        others.forEach { id ->
            val name = labelOf(id)
            SwitchRow(name, if (id in disabled) t("KAPALI · ", "OFF · ") + id else if (name != id) id else "", id in disabled) { off -> onToggle(id, off) }
        }
    }
}
