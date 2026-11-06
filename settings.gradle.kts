if (System.getenv("ANDROID_PREFS_ROOT") != null && System.getenv("ANDROID_USER_HOME") != null) {
    try {
        val pe = Class.forName("java.lang.ProcessEnvironment")
        val caseInsensitiveField = pe.getDeclaredField("theCaseInsensitiveEnvironment")
        caseInsensitiveField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = caseInsensitiveField.get(null) as MutableMap<String, String>
        map.remove("ANDROID_PREFS_ROOT")
    } catch (_: Exception) {
    }
}

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "D_R_I_V_E"
include(":app")
