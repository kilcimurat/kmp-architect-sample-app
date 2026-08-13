package com.mkilci.kmparchitect.sample.feed.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The whole iOS API of this sample framework: one factory, called by a thin Swift host.
 *
 * Swift never assembles Kotlin objects — it asks for a view controller. Keeping the exported
 * surface this narrow is what lets the sample's graph change without touching Xcode.
 */
fun FeedSampleViewController(): UIViewController {
    startFeedSampleKoinIfNeeded()
    return ComposeUIViewController { FeedSampleRoot() }
}
