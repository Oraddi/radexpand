pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RadExpand"

// core: Android'den bagimsiz genisletme motoru (bilgisayarda birim testi yapilabilir)
// app : Android klavye servisi (IME) + test/ayar ekrani
// desktop: Windows RadExpand (ayni motor ve ekranlar)
include(":core", ":app", ":desktop")
