package ule.jescuj00.fridgey.data.db

import app.cash.sqldelight.db.SqlDriver

internal const val DATABASE_NAME = "foodsaver.db"

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
