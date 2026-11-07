# Değişiklikler / Changelog

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
