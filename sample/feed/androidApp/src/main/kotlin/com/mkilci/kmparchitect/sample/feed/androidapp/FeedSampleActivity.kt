package com.mkilci.kmparchitect.sample.feed.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mkilci.kmparchitect.sample.feed.shared.FeedSampleRoot

class FeedSampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { FeedSampleRoot() }
    }
}
