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

include(":androidApp")
include(":shared")

include(":core:common")
include(":core:database")
include(":core:designsystem")
include(":core:network")
include(":core:model")
include(":core:mvi")
include(":core:navigation")
include(":core:sharing")
include(":core:ui")

include(":data:articlestore")
include(":data:feed")
include(":domain:feed")
include(":fixtures:feed")
include(":presentation:feed")
include(":sample:feed:shared")
include(":sample:feed:androidApp")