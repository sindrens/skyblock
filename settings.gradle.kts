pluginManagement {
    repositories {
        // PaperMC's repo mirrors Maven Central (avoids Central rate limits); plugin markers come from the portal.
        maven("https://repo.papermc.io/repository/maven-public/")
        gradlePluginPortal()
    }
}

plugins {
    // Downloads Java 25 automatically if it is not installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "skyblock"

include("common")
include("plugin")

// Feature modules, one per feature. Add new ones here (see CLAUDE.md "Adding a module").
include("modules:worlds")
include("modules:motd")
include("modules:moderation")
include("modules:essentials")
include("modules:hud")
include("modules:staff")
include("modules:social")
include("modules:perks")
include("modules:chat")
include("modules:menus")
include("modules:logs")
include("modules:cosmetics")
include("modules:depths")
include("modules:economy")
include("modules:skills")
// include("modules:islands")
include("modules:islands")
include("modules:auction")
include("modules:stacking")
include("modules:tools")
include("modules:profiles")
include("modules:enchants")
include("modules:wardrobe")
include("modules:trade")
include("modules:shop")
include("modules:achievements")
include("modules:afk")
include("modules:box")
include("modules:npcs")
include("modules:tutorial")
include("modules:crates")
include("modules:boosters")
include("modules:progression")
include("modules:rewards")
