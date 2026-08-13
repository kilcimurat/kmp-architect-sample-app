package com.mkilci.kmparchitect.core.sharing

/**
 * Sharing needs an `Activity` on Android and a `UIViewController` on iOS, so it is an interface
 * implemented by the hosts and injected — not `expect`/`actual`. That is the rule for any capability
 * with configuration, lifecycle, or a meaningful test double.
 *
 * Nothing here mentions a platform type: domain and presentation must never see `Context` or
 * `UIViewController`.
 */
interface Sharer {
    suspend fun share(request: ShareRequest): ShareResult
}

data class ShareRequest(
    val title: String,
    val url: String,
)

sealed interface ShareResult {
    data object Shared : ShareResult
    data object Cancelled : ShareResult

    /** The host has no share surface available — a sample, or a process without an Activity. */
    data object Unavailable : ShareResult
}
