package sp.bvantur.inspektify.ktor.list.data

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import sp.bvantur.inspektify.GetNetworkTrafficPage
import sp.bvantur.inspektify.NetworkTrafficDataLocal
import sp.bvantur.inspektify.db.InspektifyDB
import sp.bvantur.inspektify.ktor.core.data.utils.extensions.getTags
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The paging source pairs [InspektifyDB.inspektifyDBQueries] count results with page rows, so these
 * tests pin down that both queries agree on every filter combination.
 */
class TagFilterQueriesTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: InspektifyDB

    // These tests never read headers back, so the adapter only has to satisfy the database builder.
    private val headersAdapter = object : ColumnAdapter<Set<Map.Entry<String, List<String>>>, String> {
        override fun decode(databaseValue: String): Set<Map.Entry<String, List<String>>> = emptySet()

        override fun encode(value: Set<Map.Entry<String, List<String>>>): String = ""
    }

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, schema = InspektifyDB.Schema)
        database = InspektifyDB(
            driver = driver,
            NetworkTrafficDataLocalAdapter = NetworkTrafficDataLocal.Adapter(
                requestHeadersAdapter = headersAdapter,
                responseHeadersAdapter = headersAdapter
            )
        )

        insertTraffic(id = 1, method = "GET", path = "/products", host = "shop.test", status = 200)
        insertTraffic(id = 2, method = "POST", path = "/checkout", host = "shop.test", status = 500)
        insertTraffic(id = 3, method = "GET", path = "/profile", host = "user.test", status = 200)

        insertTags(1, "graphql", "search, products")
        insertTags(2, "checkout")
        // Request 3 deliberately has no tags at all.
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `GIVEN no tag selected WHEN querying THEN every request is returned`() {
        assertEquals(listOf(3L, 2L, 1L), page().map { it.id })
        assertEquals(3L, count())
    }

    @Test
    fun `GIVEN a single tag selected WHEN querying THEN only requests carrying it are returned`() {
        assertEquals(listOf(1L), page(selectedTags = setOf("graphql")).map { it.id })
        assertEquals(1L, count(selectedTags = setOf("graphql")))
    }

    @Test
    fun `GIVEN several tags selected WHEN querying THEN requests carrying any of them are returned`() {
        val selectedTags = setOf("graphql", "checkout")

        assertEquals(listOf(2L, 1L), page(selectedTags = selectedTags).map { it.id })
        assertEquals(2L, count(selectedTags = selectedTags))
    }

    @Test
    fun `GIVEN a tag containing a comma WHEN reading a page THEN the tag survives the round trip`() {
        val tags = page(selectedTags = setOf("graphql")).single().getTags()

        assertEquals(listOf("graphql", "search, products"), tags)
    }

    @Test
    fun `GIVEN a request without tags WHEN reading a page THEN its tags are empty`() {
        assertEquals(emptyList(), page().single { it.id == 3L }.getTags())
    }

    @Test
    fun `GIVEN a search query WHEN it matches only a tag THEN the request is still found`() {
        assertEquals(listOf(1L), page(searchQuery = "graph").map { it.id })
        assertEquals(1L, count(searchQuery = "graph"))
    }

    @Test
    fun `GIVEN a search query WHEN it matches a column THEN the request is found`() {
        assertEquals(listOf(2L), page(searchQuery = "checkout").map { it.id })
        assertEquals(1L, count(searchQuery = "checkout"))
    }

    @Test
    fun `GIVEN both a search query and selected tags WHEN querying THEN both filters apply`() {
        // "shop.test" matches requests 1 and 2, the tag filter narrows that down to request 1.
        val selectedTags = setOf("graphql")

        assertEquals(listOf(1L), page(searchQuery = "shop.test", selectedTags = selectedTags).map { it.id })
        assertEquals(1L, count(searchQuery = "shop.test", selectedTags = selectedTags))

        // A combination that cannot match anything must be empty rather than fall back to one filter.
        assertEquals(emptyList(), page(searchQuery = "profile", selectedTags = selectedTags).map { it.id })
        assertEquals(0L, count(searchQuery = "profile", selectedTags = selectedTags))
    }

    @Test
    fun `GIVEN paged reads WHEN walking every filter combination THEN the count always matches the rows`() {
        val searchQueries = listOf("", "shop.test", "graph", "nothing-matches")
        val tagSelections = listOf(emptySet(), setOf("graphql"), setOf("graphql", "checkout"), setOf("unknown"))

        searchQueries.forEach { searchQuery ->
            tagSelections.forEach { selectedTags ->
                val rows = page(searchQuery = searchQuery, selectedTags = selectedTags, limit = Long.MAX_VALUE)

                assertEquals(
                    rows.size.toLong(),
                    count(searchQuery = searchQuery, selectedTags = selectedTags),
                    "count and page disagree for search='$searchQuery' tags=$selectedTags"
                )
            }
        }
    }

    @Test
    fun `GIVEN a limit and offset WHEN reading pages THEN they tile the full result set`() {
        val firstPage = page(limit = 2, offset = 0).map { it.id }
        val secondPage = page(limit = 2, offset = 2).map { it.id }

        assertEquals(listOf(3L, 2L), firstPage)
        assertEquals(listOf(1L), secondPage)
        assertEquals(count(), (firstPage + secondPage).size.toLong())
    }

    @Test
    fun `GIVEN tags in the database WHEN reading distinct tags THEN they are sorted and deduplicated`() {
        insertTags(3, "graphql")

        assertEquals(
            listOf("checkout", "graphql", "search, products"),
            database.inspektifyDBQueries.getDistinctTags().executeAsList()
        )
    }

    private fun count(searchQuery: String = "", selectedTags: Set<String> = emptySet()): Long =
        database.inspektifyDBQueries.countNetworkTraffic(
            isTagFilterActive = if (selectedTags.isEmpty()) 0L else 1L,
            selectedTags = selectedTags.ifEmpty { setOf("") },
            searchQuery = searchQuery
        ).executeAsOne()

    private fun page(
        searchQuery: String = "",
        selectedTags: Set<String> = emptySet(),
        limit: Long = Long.MAX_VALUE,
        offset: Long = 0
    ): List<GetNetworkTrafficPage> = database.inspektifyDBQueries.getNetworkTrafficPage(
        isTagFilterActive = if (selectedTags.isEmpty()) 0L else 1L,
        selectedTags = selectedTags.ifEmpty { setOf("") },
        searchQuery = searchQuery,
        limit = limit,
        offset = offset
    ).executeAsList()

    @Suppress("LongParameterList")
    private fun insertTraffic(id: Long, method: String, path: String, host: String, status: Long) {
        database.inspektifyDBQueries.insertOrIgnoreNetworkTraffic(
            id = id,
            sessionId = 1,
            method = method,
            url = "https://$host$path",
            host = host,
            path = path,
            protocol = "https",
            requestTimestamp = id,
            requestHeaders = null,
            requestPayload = null,
            requestContentType = null,
            requestPayloadSize = null,
            requestHeadersSize = null,
            responseTimestamp = id,
            responseStatus = status,
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
}
