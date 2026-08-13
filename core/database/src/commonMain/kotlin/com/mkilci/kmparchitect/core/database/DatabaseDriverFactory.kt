package com.mkilci.kmparchitect.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * Opening a database is the textbook case for interface + DI rather than `expect`/`actual`: Android
 * needs a `Context`, iOS needs a file name, and tests need neither. An `expect fun` would have to
 * smuggle a `Context` through a common signature or reach for a global.
 *
 * The Android implementation therefore takes its `Context` in a constructor and is created by the
 * Android host, which is the only place that legitimately owns one.
 */
interface DatabaseDriverFactory {
    fun create(
        schema: SqlSchema<QueryResult.Value<Unit>>,
        databaseName: String,
    ): SqlDriver
}
