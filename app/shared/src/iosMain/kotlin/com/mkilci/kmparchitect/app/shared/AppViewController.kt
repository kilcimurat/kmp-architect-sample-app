package com.mkilci.kmparchitect.app.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.mkilci.kmparchitect.app.shared.di.startAppKoinIfNeeded
import com.mkilci.kmparchitect.core.database.IosDatabaseDriverFactory
import com.mkilci.kmparchitect.core.database.DatabaseDriverFactory
import com.mkilci.kmparchitect.core.sharing.ShareRequest
import com.mkilci.kmparchitect.core.sharing.ShareResult
import com.mkilci.kmparchitect.core.sharing.Sharer
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.dsl.module
import platform.UIKit.UIViewController
import kotlin.coroutines.resume

/**
 * The entire iOS API of this framework: one factory plus one narrow callback interface.
 *
 * Swift never assembles Kotlin objects. It supplies [IosShareBridge] — the one capability that
 * genuinely needs a live `UIViewController` to present from — and receives a view controller back.
 *
 * The bridge is deliberately **not** a `suspend` function: Kotlin/Native cannot have Swift implement
 * one. A completion callback is the portable shape, and [IosSharer] adapts it back into the
 * suspending [Sharer] port the domain expects.
 */
interface IosShareBridge {
    fun share(title: String, url: String, onResult: (Boolean) -> Unit)
}

private class IosSharer(private val bridge: IosShareBridge) : Sharer {
    override suspend fun share(request: ShareRequest): ShareResult =
        suspendCancellableCoroutine { continuation ->
            bridge.share(request.title, request.url) { completed ->
                if (continuation.isActive) {
                    continuation.resume(if (completed) ShareResult.Shared else ShareResult.Cancelled)
                }
            }
        }
}

fun AppViewController(shareBridge: IosShareBridge): UIViewController {
    startAppKoinIfNeeded(
        platformModules = listOf(
            module {
                single<DatabaseDriverFactory> { IosDatabaseDriverFactory() }
                single<Sharer> { IosSharer(shareBridge) }
            },
        ),
    )
    return ComposeUIViewController { App() }
}
