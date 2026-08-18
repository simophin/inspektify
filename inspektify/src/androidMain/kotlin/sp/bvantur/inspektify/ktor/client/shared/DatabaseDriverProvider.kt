package sp.bvantur.inspektify.ktor.client.shared

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import sp.bvantur.inspektify.db.InspektifyDB
import sp.bvantur.inspektify.ktor.applicationContext
import sp.bvantur.inspektify.ktor.core.data.Constants

internal actual object DatabaseDriverProvider {
    internal actual fun createDriver(): SqlDriver = AndroidSqliteDriver(
        InspektifyDB.Schema,
        applicationContext,
        Constants.DATABASE_NAME,
        callback = ForeignKeysCallback
    )

    /**
     * SQLite disables foreign keys per connection, so the cascade from NetworkTrafficDataLocal onto
     * NetworkTrafficTagLocal would never fire without this.
     *
     * It has to happen in `onOpen` rather than `onConfigure`: `PRAGMA foreign_keys` is a no-op
     * inside a transaction and `onConfigure` runs inside one.
     */
    private object ForeignKeysCallback : AndroidSqliteDriver.Callback(InspektifyDB.Schema) {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            db.setForeignKeyConstraintsEnabled(true)
        }
    }
}
