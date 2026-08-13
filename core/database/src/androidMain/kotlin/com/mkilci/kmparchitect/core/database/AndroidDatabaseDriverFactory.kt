package com.mkilci.kmparchitect.core.database

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Takes [Context] in its constructor so the Android host can build it. Nothing above this class
 * ever sees an Android type.
 */
class AndroidDatabaseDriverFactory(
    private val context: Context,
) : DatabaseDriverFactory {

    override fun create(
        schema: SqlSchema<QueryResult.Value<Unit>>,
        databaseName: String,
    ): SqlDriver = AndroidSqliteDriver(
        schema = schema,
        context = context,
        name = databaseName,
    )
}
