# Değişiklikler / Changelog

## 0.4.1

**Türkçe**
- Windows: **"Drive'a gönder" / "Drive'dan al"**, Drive masaüstü programı kurulamayan bilgisayarlar için elle taşıma. Kısayollar İndirilenler'e sabit adla kaydedilir, dosya seçili gösterilir ve tarayıcıda Drive açılır; diğer bilgisayarda indirilen dosya tek tıkla birleştirilir. RadExpand internete bağlanmaz.
- Gönderilmemiş değişiklik varsa ana pencerenin üstünde uyarı ve çıkışta "Şimdi gönderilsin mi?" sorusu.

**English**
- Windows: **"Send to Drive" / "Get from Drive"**, moving shortcuts by hand for computers where the Drive desktop program cannot be installed. Shortcuts are saved to Downloads under a fixed name, the file is shown selected and Drive opens in the browser; on the other computer the downloaded file is merged in one click. RadExpand does not connect to the internet.
- A warning at the top of the main window and an "Send now?" question on exit when there are unsent changes.

## 0.4.0

**Türkçe**
- **Kısayol arama paneli (Alt + Space)**, Espanso'daki gibi: kayıtlı kısayollar listelenir, yazarak arayın, ↑↓ ile seçin, Enter ile yazdırın, Esc ile kapatın. Android'de klavye alanında, Windows'ta küçük bir pencerede açılır. Ayarlardan kapatılabilir ya da başka bir tuşa alınabilir.
- Android: **fiziksel klavye ayrılınca ekran klavyesine otomatik geçiş** artık varsayılan; önce Samsung Klavye, yoksa Gboard seçilir (sesle yazma atlanır). Alt çubukta "Ekran klavyesi" ve "Hep otomatik geç" düğmeleri.
- Android: isteğe bağlı **"Ekran klavyesine geçiş tuşu"**: fiziksel klavyedeyken seçim penceresi açmadan Samsung Klavye'ye geçer.

**English**
- **Shortcut search panel (Alt + Space)**, like Espanso: lists your saved shortcuts; type to search, ↑↓ to select, Enter to insert, Esc to close. On Android it opens in the keyboard area, on Windows in a small window. It can be turned off or moved to another key in Settings.
- Android: **switching to the on-screen keyboard when the physical keyboard is disconnected** is now the default; Samsung Keyboard is preferred, otherwise Gboard (voice typing is skipped). The bottom bar has "On-screen keyboard" and "Always switch" buttons.
- Android: optional **"switch to on-screen keyboard" key**: switches to Samsung Keyboard without the picker while on the physical keyboard.

## 0.3.2

**Türkçe**
- **Backspace artık her zaman normal siler.** Genişletmeyi geri almak (kısa hâle dönmek) için ayrı bir tuş kullanılır: varsayılan **Shift + Backspace**. Word, Google Dokümanlar ve tarayıcılarda Shift+Backspace normal silme gibi çalışır; Ctrl+Z ile de çakışmaz. Geri alma tuşu yalnızca genişletmeden hemen sonra iş görür; başka zaman tuş normal işini yapar.
- Ayarlar → "Genişletmeyi geri alma": Shift + Backspace / özel tuş (ör. F4) / Backspace (eski davranış) / Kapalı.
- Ayarlar → "Uygulama bazında kapatma": Chrome, Word, Excel, Google Dokümanlar, Gmail, Outlook, WhatsApp, Samsung Notes, parola yöneticileri gibi yaygın uygulamalar hazır listede (Android'de yalnızca yüklü olanlar gösterilir). Windows'ta program adları büyük/küçük harf farkı gözetmeden eşleşir.

**English**
- **Backspace now always deletes normally.** A separate key undoes an expansion (back to the short form): **Shift + Backspace** by default. In Word, Google Docs and browsers Shift+Backspace works like a normal delete, and it does not clash with Ctrl+Z. The undo key only acts right after an expansion; at any other time it does its normal job.
- Settings → "Undo an expansion": Shift + Backspace / custom key (e.g. F4) / Backspace (old behaviour) / Off.
- Settings → "Turn off per app": common apps such as Chrome, Word, Excel, Google Docs, Gmail, Outlook, WhatsApp, Samsung Notes and password managers are listed ready to switch off (on Android only installed ones are shown). On Windows, program names match regardless of letter case.

## 0.3.1

**Türkçe**
- Ayarlar → "Kısayol büyük harfle yazılınca" bölümüne yeni seçenek: **Metin olduğu gibi**. `Rp` + Space yazınca `Rapor` değil, kayıttaki gibi `rapor` yazılır. Diğer seçenekler: Genişlemesin / İlk harf büyük / İlk harf + tamamı büyük.
- "Nasıl kullanılır" rehberinde ve açıklama sayfasında daha genel örnekler (`rp` → rapor, `rpt` → report); örnek pakete bu kısayollar ve bir toplantı şablonu eklendi.

**English**
- New option in Settings → "When a shortcut is typed with capitals": **Text as saved**. Typing `Rpt` + Space gives `report` exactly as saved instead of `Report`. Other options: Don't expand / First letter capital / First letter + all caps.
- More general examples in the "How to use" guide and on the project page (`rp` → rapor, `rpt` → report); the sample pack now includes them plus a meeting template.

## 0.3.0 — ilk herkese açık sürüm / first public release

**Türkçe**
- Uygulamanın yeni adı **RadExpand**.
- Arayüz **Türkçe ve İngilizce**; dil ilk açılışta sorulur.
- Yeni **Ayarlar**:
  - Space / Enter / Tab görevleri ve özel genişletme tuşu;
  - kelime kuralları (kesme işareti, tire, eğik çizgi), büyük harf;
  - tema, renk, yazı boyutu;
  - yedek sayısı, senkron, güvenlik, sıfırlama.
- Uygulama içinde **Nasıl kullanılır** rehberi ve isteğe bağlı **örnek radyoloji paketi**.
- Şablonlarda birden fazla `$|$` (Tab ile sonraki alana geçiş), `{tarih}` / `{saat}` değişkenleri.
- Kısayol başına kullanım sayısı, "en çok kullanılan önce" sıralaması, "hiç kullanılmayanlar" filtresi.
- Sık kullanılan kelimeye benzeyen kısayollar için uyarı.
- Android:
  - bildirim panelinde aç/kapa kutucuğu;
  - klavye ayrılınca önceki klavyeye geçme seçeneği.
- Windows:
  - kurulum dosyası (.msi, yönetici izni gerekmez);
  - güncelleme kontrolü ve "İndir ve kur";
  - duraklat kısayolu, yazma hızı, uzun metni pano ile yapıştırma;
  - Espanso ya da eski MedExpand çalışıyorsa uyarı.

**English**
- The app is now called **RadExpand**.
- **Turkish and English** interface; the language is chosen at first launch.
- New **Settings**:
  - what Space / Enter / Tab do, plus a custom expand key;
  - word rules (apostrophe, hyphen, slash) and capitalisation;
  - theme, colour, text size;
  - backup count, sync, security, reset.
- In-app **How to use** guide and an optional **sample radiology pack**.
- Multiple `$|$` fields in templates (Tab jumps to the next one); `{date}` / `{time}` variables.
- Per-shortcut usage count, "most used first" sort and a "never used" filter.
- A warning when a shortcut looks like a common word.
- Android:
  - Quick Settings on/off tile;
  - option to switch back to the previous keyboard when the physical keyboard disconnects.
- Windows:
  - installer (.msi, no admin rights);
  - update check with "Download and install";
  - pause hotkey, typing speed, paste long texts via the clipboard;
  - a warning when Espanso or the old MedExpand is running.
