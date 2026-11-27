# Third-party components / Üçüncü taraf bileşenler

RadExpand uses the following open-source components. Each is distributed under its own license.
RadExpand aşağıdaki açık kaynak bileşenleri kullanır; her biri kendi lisansıyla dağıtılır.

| Component | Used in | License |
|---|---|---|
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization (JetBrains) | Android, Windows | Apache License 2.0 |
| AndroidX, Jetpack Compose, Material 3 (Google) | Android | Apache License 2.0 |
| Compose Multiplatform (JetBrains) | Windows | Apache License 2.0 |
| Material Icons (Google) | Android, Windows | Apache License 2.0 |
| SnakeYAML Engine | Android, Windows | Apache License 2.0 |
| JNA – Java Native Access | Windows | Apache License 2.0 (dual-licensed with LGPL 2.1; used under Apache 2.0) |
| Eclipse Temurin Java runtime (bundled in the Windows app) | Windows | GPL v2 with Classpath Exception |
| Turkish word frequency list (`core/src/main/resources/dict/tr_general.txt`), derived from **FrequencyWords** by Hermit Dave (OpenSubtitles 2018). Changes: filtered (short words, digits, foreign letters, offensive words removed), counts removed | Android, Windows | **CC BY-SA 4.0** — https://github.com/hermitdave/FrequencyWords · https://creativecommons.org/licenses/by-sa/4.0/ |

The medical/radiology term lists (`tr_medical.txt`, `en_medical.txt`) were compiled for RadExpand and are covered by the project's MIT licence.
Tıp/radyoloji terim listeleri RadExpand için derlenmiştir ve projenin MIT lisansı kapsamındadır.

License texts / Lisans metinleri:
- Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
- GPL v2 with Classpath Exception: https://openjdk.org/legal/gplv2+ce.html

RadExpand is not affiliated with Espanso; it can only read and write Espanso's open YAML file format.
RadExpand'in Espanso ile bir bağlantısı yoktur; yalnızca Espanso'nun açık YAML dosya biçimini okuyup yazabilir.
