package com.mkilci.kmparchitect.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProjectNamingTest {

    @Test
    fun `namespace is derived from every path segment`() {
        assertEquals("com.mkilci.kmparchitect.core.mvi", ProjectNaming.namespaceFor(":core:mvi"))
        assertEquals("com.mkilci.kmparchitect.domain.feed", ProjectNaming.namespaceFor(":domain:feed"))
        assertEquals(
            "com.mkilci.kmparchitect.sample.feed.shared",
            ProjectNaming.namespaceFor(":sample:feed:shared"),
        )
    }

    @Test
    fun `namespace strips characters that are illegal in a package name`() {
        assertEquals(
            "com.mkilci.kmparchitect.core.designsystem",
            ProjectNaming.namespaceFor(":core:design-system"),
        )
    }

    @Test
    fun `namespace rejects a path with no usable segments`() {
        assertFailsWith<IllegalArgumentException> { ProjectNaming.namespaceFor(":") }
    }

    @Test
    fun `framework name distinguishes projects that are all named shared`() {
        assertEquals("AppShared", ProjectNaming.frameworkBaseNameFor(":app:shared"))
        assertEquals("FeedSample", ProjectNaming.frameworkBaseNameFor(":sample:feed:shared"))
        assertEquals("BookmarksSample", ProjectNaming.frameworkBaseNameFor(":sample:bookmarks:shared"))
    }

    @Test
    fun `framework names are unique across every framework project in this repository`() {
        val paths = listOf(
            ":app:shared",
            ":sample:feed:shared",
            ":sample:article:shared",
            ":sample:bookmarks:shared",
        )
        val names = paths.map(ProjectNaming::frameworkBaseNameFor)
        assertEquals(names.size, names.toSet().size, "duplicate framework base names: $names")
    }

    @Test
    fun `framework name falls back to PascalCase of all segments`() {
        assertEquals("CoreDesignSystem", ProjectNaming.frameworkBaseNameFor(":core:design-system"))
    }
}
