import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.radexpand.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.radexpand.app"
        minSdk = 26 // Android 8.0+
        targetSdk = 35
        versionCode = (project.property("radexpandVersionCode") as String).toInt()
        versionName = project.property("radexpandVersion") as String
    }

    // Sabit imza anahtari: yeni surumler eskisinin ustune (veriler silinmeden) kurulabilsin.
    // Anahtar ve sifre DEPODA YOKTUR: yerelde keystore/keystore.properties, GitHub'da gizli degiskenler (Secrets).
    val signProps = Properties().apply {
        val f = rootProject.file("keystore/keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun sign(key: String, env: String): String? = System.getenv(env) ?: signProps.getProperty(key)
    val storePath = sign("storeFile", "RADEXPAND_KEYSTORE")?.let { if (File(it).isAbsolute) File(it) else rootProject.file("keystore/$it") }
    val canSign = storePath?.exists() == true

    signingConfigs {
        if (canSign) create("radexpand") {
            storeFile = storePath
            storePassword = sign("storePassword", "RADEXPAND_STORE_PASSWORD")
            keyAlias = sign("keyAlias", "RADEXPAND_KEY_ALIAS") ?: "radexpand"
            keyPassword = sign("keyPassword", "RADEXPAND_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (canSign) signingConfig = signingConfigs.getByName("radexpand")
        }
        debug {
            if (canSign) signingConfig = signingConfigs.getByName("radexpand")
        }
    }

    buildFeatures {
        compose = true
    }

    // Windows uygulamasiyla ortak Compose ekranlari (kisayol listesi, duzenleme, ice aktarma onizlemesi)
    sourceSets["main"].java.srcDir("../ui-shared/src")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/versions/9/previous-compilation-data.bin")
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
