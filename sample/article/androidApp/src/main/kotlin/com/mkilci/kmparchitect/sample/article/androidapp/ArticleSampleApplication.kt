package com.mkilci.kmparchitect.sample.article.androidapp

import android.app.Application
import com.mkilci.kmparchitect.sample.article.shared.startArticleSampleKoinIfNeeded

class ArticleSampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startArticleSampleKoinIfNeeded()
    }
}
