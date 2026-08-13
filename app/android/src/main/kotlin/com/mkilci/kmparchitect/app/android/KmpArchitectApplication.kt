package com.mkilci.kmparchitect.app.android

import android.app.Application
import com.mkilci.kmparchitect.app.shared.di.startAppKoinIfNeeded
import com.mkilci.kmparchitect.core.database.AndroidDatabaseDriverFactory
import com.mkilci.kmparchitect.core.database.DatabaseDriverFactory
import com.mkilci.kmparchitect.core.sharing.Sharer
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The host contributes exactly two things: the platform objects only it can build.
 *
 * Which repository is live, which use cases exist and which ViewModels are defined were all decided
 * in `app/shared`. That is why adding a feature does not touch this file.
 */
class KmpArchitectApplication : Application() {

    private val activityHolder = CurrentActivityHolder()

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(activityHolder)

        startAppKoinIfNeeded(
            platformModules = listOf(
                module {
                    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(androidContext()) }
                    single<Sharer> { AndroidSharer(activityHolder) }
                },
            ),
            configure = { androidContext(this@KmpArchitectApplication) },
        )
    }
}
