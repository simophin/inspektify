package sp.bvantur.inspektify.ktor.list.data

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import sp.bvantur.inspektify.NetworkTrafficDataLocal
import sp.bvantur.inspektify.db.InspektifyDB
import sp.bvantur.inspektify.ktor.client.shared.DatabaseDriverProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Foreign keys are a per connection setting in SQLite and default to off, so the cascade from
 * NetworkTrafficDataLocal onto NetworkTrafficTagLocal is only real if the driver turns them on.
 * These tests cover the JVM driver; the Android and iOS drivers enable the same pragma but cannot
 * be exercised from here.
 */
class TagForeignKeyTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: InspektifyDB

    private val headersAdapter = object : ColumnAdapter<Set<Map.Entry<String, List<String>>>, String> {
        override fun decode(databaseValue: String): Set<Map.Entry<String, List<String>>> = emptySet()

        override fun encode(value: Set<Map.Entry<String, List<String>>>): String = ""
    }

    @BeforeTest
    fun setup() {
        // Built exactly like the production desktop driver, including its connection properties.
        driver = JdbcSqliteDriver(
            JdbcSqliteDriver.IN_MEMORY,
            DatabaseDriverProvider.foreignKeysEnabledProperties(),
            schema = InspektifyDB.Schema
        )
        database = InspektifyDB(
            driver = driver,
            NetworkTrafficDataLocalAdapter = NetworkTrafficDataLocal.Adapter(
                requestHeadersAdapter = headersAdapter,
                responseHeadersAdapter = headersAdapter
            )
        )
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `GIVEN a driver created connection WHEN reading the pragma THEN foreign keys are enabled`() {
        assertEquals(1L, driver.longPragma("PRAGMA foreign_keys"))
    }

    @Test
    fun `GIVEN a request with tags WHEN the request is deleted THEN its tags are cascaded away`() {
        insertTraffic(id = 1)
        insertTraffic(id = 2)
        insertTags(1, "graphql")
        insertTags(2, "checkout")

        database.inspektifyDBQueries.removeRowsBySessionId(SESSION_ID)

        assertEquals(emptyList(), database.inspektifyDBQueries.getDistinctTags().executeAsList())
    }

    @Test
    fun `GIVEN a request with tags WHEN only that request is deleted THEN other tags survive`() {
        insertTraffic(id = 1)
        insertTraffic(id = 2, requestTimestamp = 5_000)
        insertTags(1, "graphql")
        insertTags(2, "checkout")

        // Removes request 1 only, which is older than the cutoff.
        database.inspektifyDBQueries.removeNetworkTrafficOlderThan(1_000)

        assertEquals(listOf("checkout"), database.inspektifyDBQueries.getDistinctTags().executeAsList())
    }

    @Test
    fun `GIVEN no matching request WHEN inserting a tag THEN the foreign key is enforced`() {
        val failure = assertFailsWith<Exception> {
            database.inspektifyDBQueries.insertNetworkTrafficTag(networkTrafficId = 404, tag = "orphan")
        }

        assertTrue(
            failure.message.orEmpty().contains("FOREIGN KEY", ignoreCase = true),
            "expected a foreign key violation but was: ${failure.message}"
        )
    }

    @Test
    fun `GIVEN a request saved twice under the same id WHEN the response arrives THEN its tags survive`() {
        // Mirrors the request phase followed by the response phase of InspektifyKtorClient.
        saveTraffic(id = 1, responseStatus = null)
        insertTags(1, "graphql", "checkout")

        saveTraffic(id = 1, responseStatus = 200)

        assertEquals(
            listOf("checkout", "graphql"),
            database.inspektifyDBQueries.getTagsByNetworkTrafficId(1).executeAsList()
        )
        assertEquals(200L, database.inspektifyDBQueries.getNetworkTrafficById(1).executeAsOne().responseStatus)
    }

    @Test
    fun `GIVEN a response phase without tags WHEN it is saved THEN the tags of the request survive`() {
        saveTraffic(id = 1, responseStatus = null)
        insertTags(1, "graphql")

        // The response phase deliberately writes no tag rows at all here.
        saveTraffic(id = 1, responseStatus = 500)

        assertEquals(listOf("graphql"), database.inspektifyDBQueries.getTagsByNetworkTrafficId(1).executeAsList())
    }

    @Test
    fun `GIVEN a version 3 database WHEN migrating THEN the tag table references the live parent table`() {
        val migratedDriver = JdbcSqliteDriver(
            JdbcSqliteDriver.IN_MEMORY,
            DatabaseDriverProvider.foreignKeysEnabledProperties()
        )
        try {
            migratedDriver.createVersion3Schema()
            migratedDriver.execute(
                null,
                "INSERT INTO NetworkTrafficDataLocal (id, sessionId, method, tags) VALUES (7, 1, 'GET', '[\"a\"]')",
                0
            )

            InspektifyDB.Schema.migrate(migratedDriver, 3, 4)

            // The rename during the rebuild must not have repointed the foreign key at the temporary
            // table: column 2 of foreign_key_list is the referenced table.
            assertEquals(
                listOf("NetworkTrafficDataLocal"),
                migratedDriver.stringColumn("PRAGMA foreign_key_list(NetworkTrafficTagLocal)", columnIndex = 2)
            )
            // A dangling reference only shows up when the constraint is actually exercised.
            migratedDriver.execute(null, "INSERT INTO NetworkTrafficTagLocal VALUES (7, 'a')", 0)
            assertEquals(0L, migratedDriver.longPragma("PRAGMA foreign_key_check") ?: 0L)
            // The rebuilt parent table kept its rows and lost the old JSON column.
            assertEquals(listOf("7"), migratedDriver.stringColumn("SELECT id FROM NetworkTrafficDataLocal", 0))
            assertTrue(
                "tags" !in migratedDriver.stringColumn("PRAGMA table_info(NetworkTrafficDataLocal)", columnIndex = 1)
            )
        } finally {
            migratedDriver.close()
        }
    }

    private fun saveTraffic(id: Long, responseStatus: Long?) {
        database.transaction {
            database.inspektifyDBQueries.insertOrIgnoreNetworkTraffic(
                id = id,
                sessionId = SESSION_ID,
                method = "GET",
                url = "https://example.test/path",
                host = "example.test",
                path = "/path",
                protocol = "https",
                requestTimestamp = id,
                requestHeaders = null,
                requestPayload = null,
                requestContentType = null,
                requestPayloadSize = null,
                requestHeadersSize = null,
                responseTimestamp = null,
                responseStatus = responseStatus,
                responseStatusDescription = null,
                responseHeaders = null,
                responsePayload = null,
                responseContentType = null,
                responsePayloadSize = null,
                responseHeadersSize = null,
                tookDurationInMs = null
            )
            database.inspektifyDBQueries.updateNetworkTraffic(
                id = id,
                sessionId = SESSION_ID,
                method = "GET",
                url = "https://example.test/path",
                host = "example.test",
                path = "/path",
                protocol = "https",
                requestTimestamp = id,
                requestHeaders = null,
                requestPayload = null,
                requestContentType = null,
                requestPayloadSize = null,
                requestHeadersSize = null,
                responseTimestamp = null,
                responseStatus = responseStatus,
                responseStatusDescription = null,
                responseHeaders = null,
                responsePayload = null,
                responseContentType = null,
                responsePayloadSize = null,
                responseHeadersSize = null,
                tookDurationInMs = null
            )
        }
    }

    private fun insertTraffic(id: Long, requestTimestamp: Long = id) {
        database.inspektifyDBQueries.insertOrIgnoreNetworkTraffic(
            id = id,
            sessionId = SESSION_ID,
            method = "GET",
            url = "https://example.test/path",
            host = "example.test",
            path = "/path",
            protocol = "https",
            requestTimestamp = requestTimestamp,
            requestHeaders = null,
            requestPayload = null,
            requestContentType = null,
            requestPayloadSize = null,
            requestHeadersSize = null,
            responseTimestamp = null,
            responseStatus = null,
            responseStatusDescription = null,
            responseHeaders = null,
            responsePayload = null,
            responseContentType = null,
            responsePayloadSize = null,
            responseHeadersSize = null,
            tookDurationInMs = null
        )
    }

    private fun insertTags(networkTrafficId: Long, vararg tags: String) {
        tags.forEach { tag ->
            database.inspektifyDBQueries.insertNetworkTrafficTag(networkTrafficId = networkTrafficId, tag = tag)
        }
    }

    private companion object {
        const val SESSION_ID = 1L
    }
}

/** Schema as it looked at version 3, i.e. before the tag table was introduced. */
private fun SqlDriver.createVersion3Schema() {
    execute(
        null,
        """
        CREATE TABLE NetworkTrafficDataLocal (
          id INTEGER NOT NULL PRIMARY KEY,
          sessionId INTEGER NOT NULL DEFAULT 0,
          method TEXT,
          url TEXT,
          host TEXT,
          path TEXT,
          protocol TEXT,
          tags TEXT,
          requestTimestamp INTEGER,
          requestHeaders TEXT,
          requestPayload TEXT,
          requestContentType TEXT,
          requestPayloadSize INTEGER,
          requestHeadersSize INTEGER,
          responseTimestamp INTEGER,
          responseStatus INTEGER,
          responseStatusDescription TEXT,
          responseHeaders TEXT,
          responsePayload TEXT,
          responseContentType TEXT,
          responsePayloadSize INTEGER,
          responseHeadersSize INTEGER,
          tookDurationInMs INTEGER
        )
        """.trimIndent(),
        0
    )
}

private fun SqlDriver.longPragma(sql: String): Long? = executeQuery(
    null,
    sql,
    { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
    0
).value

private fun SqlDriver.stringColumn(sql: String, columnIndex: Int): List<String> = executeQuery(
    null,
    sql,
    { cursor -> QueryResult.Value(cursor.collectStrings(columnIndex)) },
    0
).value

private fun SqlCursor.collectStrings(columnIndex: Int): List<String> {
    val values = mutableListOf<String>()
    while (next().value) {
        values += getString(columnIndex).orEmpty()
    }
    return values
}
