plugins {
    id("kmpa.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Justified `api`: FakeFeedRepository implements FeedRepository and FeedFixtures exposes
            // Article, both of which consumers name directly.
            api(project(":domain:feed"))
        }
    }
}
