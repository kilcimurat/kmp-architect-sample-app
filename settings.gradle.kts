rootProject.name = "KmpArchitectSampleApp"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":app:shared")
include(":app:android")

include(":core:common")
include(":core:database")
include(":core:designsystem")
include(":core:network")
include(":core:model")
include(":core:mvi")
include(":core:navigation")
include(":core:sharing")
include(":core:ui")

include(":data:article")
include(":data:articlestore")
include(":data:bookmarks")
include(":data:feed")
include(":domain:article")
include(":domain:bookmarks")
include(":domain:feed")
include(":fixtures:article")
include(":fixtures:bookmarks")
include(":fixtures:feed")
include(":presentation:article")
include(":presentation:bookmarks")
include(":presentation:feed")
include(":sample:article:shared")
include(":sample:article:androidApp")
include(":sample:bookmarks:shared")
include(":sample:bookmarks:androidApp")
include(":sample:feed:shared")
include(":sample:feed:androidApp")