package ule.jescuj00.fridgey.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.osmerion.android.database.sqlite.OsmerionSQLiteOpenHelperFactory
import ule.jescuj00.fridgey.database.FoodSaverDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    // Use a bundled modern SQLite instead of the framework one: it guarantees
    // FTS5 + the unicode61 `remove_diacritics 2` tokenizer used by ProductoFts
    // across all Android versions/OEMs (framework SQLite at minSdk 24 does not).
    // The on-disk file format is unchanged, so existing on-device databases open
    // normally and migrations still run.
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(
            schema = FoodSaverDatabase.Schema,
            context = context,
            name = DATABASE_NAME,
            factory = OsmerionSQLiteOpenHelperFactory(),
        )
}
