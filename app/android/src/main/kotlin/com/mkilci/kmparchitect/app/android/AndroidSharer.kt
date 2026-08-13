package com.mkilci.kmparchitect.app.android

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.mkilci.kmparchitect.core.sharing.ShareRequest
import com.mkilci.kmparchitect.core.sharing.ShareResult
import com.mkilci.kmparchitect.core.sharing.Sharer

/**
 * Tracks the foreground Activity so sharing can be launched from it.
 *
 * A share sheet needs an Activity, and nothing above the host is allowed to hold one. Keeping the
 * reference here — cleared on pause — is the host's job precisely so that `Context` never appears
 * in a domain or presentation signature.
 */
class CurrentActivityHolder : Application.ActivityLifecycleCallbacks {

    @Volatile
    var current: Activity? = null
        private set

    override fun onActivityResumed(activity: Activity) {
        current = activity
    }

    override fun onActivityPaused(activity: Activity) {
        if (current === activity) current = null
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (current === activity) current = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

/**
 * Reports [ShareResult.Shared] once the chooser has been launched.
 *
 * Android does not tell an app whether the user completed or abandoned a plain
 * `Intent.ACTION_SEND` chooser; learning that requires the Android 12+ chooser result API. Rather
 * than invent a cancellation signal, this implementation reports what it actually knows, and
 * [ShareResult.Cancelled] is left to platforms that genuinely report it.
 */
class AndroidSharer(
    private val activityHolder: CurrentActivityHolder,
) : Sharer {

    override suspend fun share(request: ShareRequest): ShareResult {
        val activity = activityHolder.current ?: return ShareResult.Unavailable

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, request.title)
            putExtra(Intent.EXTRA_TEXT, "${request.title}\n${request.url}")
        }
        activity.startActivity(Intent.createChooser(intent, request.title))
        return ShareResult.Shared
    }
}
