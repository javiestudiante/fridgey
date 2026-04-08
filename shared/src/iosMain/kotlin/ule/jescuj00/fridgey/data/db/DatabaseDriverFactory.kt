package ule.jescuj00.fridgey.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import ule.jescuj00.fridgey.database.FoodSaverDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(FoodSaverDatabase.Schema, DATABASE_NAME)
}
