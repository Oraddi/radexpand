# Gradle'i tasinabilir araclarla calistirir (JDK 21, Android SDK, Gradle 8.10.2).
# Araclar klasoru: RADEXPAND_TOOLS ortam degiskeni; yoksa proje klasorunun yanindaki "tools" klasoru.
# Ornek:  .\gradlew-local.ps1 :core:test
$tools = if ($env:RADEXPAND_TOOLS) { $env:RADEXPAND_TOOLS } else { Join-Path (Split-Path -Parent $PSScriptRoot) 'tools' }
$env:JAVA_HOME = (Get-ChildItem $tools -Directory | Where-Object Name -like 'jdk-21*' | Select-Object -First 1).FullName
$env:ANDROID_HOME = Join-Path $tools 'android-sdk'
$env:GRADLE_USER_HOME = Join-Path $tools 'gradle-home'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& (Join-Path $tools 'gradle-8.10.2\bin\gradle.bat') --console=plain -p $PSScriptRoot @args
exit $LASTEXITCODE