package com.mkilci.kmparchitect.sample.feed.androidapp

import android.app.Application
import com.mkilci.kmparchitect.sample.feed.shared.startFeedSampleKoinIfNeeded

/**
 * The native host owns executable lifecycle and nothing else. Which implementations are active is
 * decided by the shared sample root, so this file does not change when the feature's graph does.
 */
class FeedSampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startFeedSampleKoinIfNeeded()
    }
}
