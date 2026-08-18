package sp.bvantur.inspektify.ktor.client.shared

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import sp.bvantur.inspektify.db.InspektifyDB
import sp.bvantur.inspektify.ktor.core.data.Constants
import java.io.File
import java.util.Properties

internal actual object DatabaseDriverProvider {
    internal actual fun createDriver(): SqlDriver {
        val databaseFolder = File("build/generated/inspektify")
        if (!databaseFolder.exists()) {
            databaseFolder.mkdirs()
        }

        return JdbcSqliteDriver(
            "jdbc:sqlite:build/generated/inspektify/${Constants.DATABASE_NAME}",
            foreignKeysEnabledProperties(),
            schema = InspektifyDB.Schema
        )
    }

    /**
     * Foreign keys are a per connection setting in SQLite, and the driver opens more than one
     * connection, so the pragma is handed to the JDBC driver as a property instead of being executed
     * once. Without it the cascade onto NetworkTrafficTagLocal would silently do nothing.
     */
    internal fun foreignKeysEnabledProperties(): Properties = Properties().apply {
        setProperty("foreign_keys", "on")
    }
}
