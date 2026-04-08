package ule.jescuj00.fridgey.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import ule.jescuj00.fridgey.database.FoodSaverDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(FoodSaverDatabase.Schema, context, DATABASE_NAME)
}
