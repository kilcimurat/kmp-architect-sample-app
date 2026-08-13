package com.mkilci.kmparchitect.sample.article.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun ArticleSampleViewController(): UIViewController {
    startArticleSampleKoinIfNeeded()
    return ComposeUIViewController { ArticleSampleRoot() }
}
