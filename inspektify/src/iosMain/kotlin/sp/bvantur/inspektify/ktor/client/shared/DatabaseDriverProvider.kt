package sp.bvantur.inspektify.ktor.client.shared

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import sp.bvantur.inspektify.db.InspektifyDB
import sp.bvantur.inspektify.ktor.core.data.Constants

internal actual object DatabaseDriverProvider {
    // Foreign keys are off by default on every SQLite connection, so they are turned on here to let
    // the cascade from NetworkTrafficDataLocal onto NetworkTrafficTagLocal actually fire.
    internal actual fun createDriver(): SqlDriver = NativeSqliteDriver(
        schema = InspektifyDB.Schema,
        name = Constants.DATABASE_NAME,
        onConfiguration = { configuration ->
            configuration.copy(
                extendedConfig = configuration.extendedConfig.copy(foreignKeyConstraints = true)
            )
        }
    )
}
