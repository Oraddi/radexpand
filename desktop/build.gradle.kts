import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    // Android uygulamasiyla ortak Compose ekranlari
    sourceSets["main"].kotlin.srcDir("../ui-shared/src")
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("net.java.dev.jna:jna-platform:5.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

val appVersion = project.property("radexpandVersion") as String

compose.desktop {
    application {
        mainClass = "com.radexpand.desktop.MainKt"
        jvmArgs += listOf("-Xmx256m", "-Dfile.encoding=UTF-8", "-Dradexpand.version=$appVersion")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "RadExpand"
            packageVersion = appVersion
            description = "RadExpand text expander"
            vendor = "RadExpand"
            modules("java.instrument", "java.management", "java.naming", "java.prefs", "java.net.http", "jdk.unsupported", "jdk.crypto.ec")
            windows {
                menuGroup = "RadExpand"
                // Kullanici bazinda kurulum: yonetici izni (UAC) gerekmez; guncelleme de sessizce ustune kurulur.
                perUserInstall = true
                dirChooser = false
                shortcut = true
                menu = true
                iconFile.set(project.file("icons/radexpand.ico"))
                upgradeUuid = "6f3c1a52-8d1e-4b8a-9a9b-2c7f1d0e5a41"
            }
        }
    }
}
