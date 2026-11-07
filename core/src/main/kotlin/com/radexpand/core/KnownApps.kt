package com.radexpand.core

/**
 * "Uygulama bazinda kapatma" listesinde hazir gosterilen yaygin uygulamalar.
 * Kimlik: Android'de paket adi, Windows'ta .exe adi (kucuk harf).
 */
object KnownApps {
    data class App(val id: String, val name: String)

    val android: List<App> = listOf(
        App("com.android.chrome", "Chrome"),
        App("com.sec.android.app.sbrowser", "Samsung Internet"),
        App("org.mozilla.firefox", "Firefox"),
        App("com.microsoft.emmx", "Microsoft Edge"),
        App("com.google.android.apps.docs.editors.docs", "Google Dokümanlar / Docs"),
        App("com.google.android.apps.docs.editors.sheets", "Google E-Tablolar / Sheets"),
        App("com.google.android.keep", "Google Keep"),
        App("com.google.android.gm", "Gmail"),
        App("com.microsoft.office.word", "Microsoft Word"),
        App("com.microsoft.office.excel", "Microsoft Excel"),
        App("com.microsoft.office.powerpoint", "Microsoft PowerPoint"),
        App("com.microsoft.office.officehubrow", "Microsoft 365 (Office)"),
        App("com.microsoft.office.outlook", "Microsoft Outlook"),
        App("com.microsoft.office.onenote", "Microsoft OneNote"),
        App("com.microsoft.teams", "Microsoft Teams"),
        App("com.samsung.android.app.notes", "Samsung Notes"),
        App("com.samsung.android.messaging", "Samsung Mesajlar / Messages"),
        App("com.google.android.apps.messaging", "Google Mesajlar / Messages"),
        App("com.whatsapp", "WhatsApp"),
        App("org.telegram.messenger", "Telegram"),
        App("com.x8bit.bitwarden", "Bitwarden"),
        App("com.agilebits.onepassword", "1Password"),
    )

    val windows: List<App> = listOf(
        App("chrome.exe", "Google Chrome"),
        App("msedge.exe", "Microsoft Edge"),
        App("firefox.exe", "Firefox"),
        App("winword.exe", "Microsoft Word"),
        App("excel.exe", "Microsoft Excel"),
        App("powerpnt.exe", "Microsoft PowerPoint"),
        App("outlook.exe", "Microsoft Outlook"),
        App("olk.exe", "Outlook (yeni / new)"),
        App("onenote.exe", "Microsoft OneNote"),
        App("ms-teams.exe", "Microsoft Teams"),
        App("notepad.exe", "Not Defteri / Notepad"),
        App("whatsapp.exe", "WhatsApp"),
        App("whatsapp.root.exe", "WhatsApp (Store)"),
        App("telegram.exe", "Telegram"),
        App("keepass.exe", "KeePass"),
        App("keepassxc.exe", "KeePassXC"),
        App("1password.exe", "1Password"),
        App("bitwarden.exe", "Bitwarden"),
    )
}
