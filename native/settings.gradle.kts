pluginManagement {
    repositories {
        // Google's mirror of Maven Central: cloud machines get 429s from Maven Central itself.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // Google's mirror of Maven Central: cloud machines get 429s from Maven Central itself.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        google()
        mavenCentral()
    }
}
rootProject.name = "quiet-native"
include(":app")
