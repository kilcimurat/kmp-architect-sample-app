package com.mkilci.kmparchitect.sample.bookmarks.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun BookmarksSampleViewController(): UIViewController {
    startBookmarksSampleKoinIfNeeded()
    return ComposeUIViewController { BookmarksSampleRoot() }
}
