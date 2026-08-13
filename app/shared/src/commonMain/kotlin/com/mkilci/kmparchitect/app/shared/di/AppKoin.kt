package com.mkilci.kmparchitect.app.shared.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module

private var started = false

/**
 * Idempotent startup shared by both hosts.
 *
 * [platformModules] is where a host contributes what only it can build: the SQLite driver factory,
 * and the share surface. Everything else is already decided in [sharedProductionModules].
 */
fun startAppKoinIfNeeded(
    platformModules: List<Module>,
    configure: KoinApplication.() -> Unit = {},
) {
    if (started) return
    started = true
    startKoin {
        configure()
        modules(sharedProductionModules() + platformModules)
    }
}

fun resetAppKoin() {
    if (!started) return
    stopKoin()
    started = false
}
