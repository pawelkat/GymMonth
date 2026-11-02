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

rootProject.name = "GymMonth"
include(":app")

// VaultWrite library (local Obsidian vault writes). Sibling checkout: ../VaultWrite
// (Termux: /root/VaultWrite next to /root/gymmonth). Override with -PvaultWriteDir=/path.
val vaultWriteDir = providers.gradleProperty("vaultWriteDir").orNull?.let(::File)
    ?: File(rootDir, "../VaultWrite")
include(":vaultwrite")
project(":vaultwrite").projectDir = File(vaultWriteDir, "vaultwrite")
