package sp.bvantur.inspektify.ktor.core.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * A [PagingSource] that loads discrete pages out of SQLDelight with `LIMIT`/`OFFSET`.
 *
 * Every load runs inside a single transaction so that the total count and the page rows are
 * consistent with each other. The currently loaded page query is kept as a listener on the
 * database, so any write to the underlying table invalidates this source and Paging transparently
 * creates a new one - which is what keeps the traffic list live while requests are flowing in.
 */
internal class OffsetQueryPagingSource<RowType : Any>(
    private val transacter: Transacter,
    private val context: CoroutineContext,
    private val countQuery: () -> Query<Long>,
    private val pageQuery: (limit: Long, offset: Long) -> Query<RowType>
) : PagingSource<Int, RowType>(),
    Query.Listener {

    private var currentQuery: Query<RowType>? = null

    override val jumpingSupported: Boolean = true

    init {
        registerInvalidatedCallback {
            currentQuery?.removeListener(this)
            currentQuery = null
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RowType> = withContext(context) {
        val key = params.key ?: 0
        val limit = when (params) {
            is LoadParams.Prepend -> minOf(key, params.loadSize)
            else -> params.loadSize
        }

        val page = transacter.transactionWithResult {
            val count = countQuery().executeAsOne().toInt()
            val offset = when (params) {
                is LoadParams.Prepend -> maxOf(0, key - params.loadSize)
                is LoadParams.Append -> key
                is LoadParams.Refresh -> if (key >= count - params.loadSize) {
                    maxOf(0, count - params.loadSize)
                } else {
                    key
                }
            }

            val query = pageQuery(limit.toLong(), offset.toLong())
            currentQuery?.removeListener(this@OffsetQueryPagingSource)
            currentQuery = query
            query.addListener(this@OffsetQueryPagingSource)

            val data = query.executeAsList()
            val nextOffset = offset + data.size

            LoadResult.Page(
                data = data,
                prevKey = offset.takeIf { it > 0 && data.isNotEmpty() },
                nextKey = nextOffset.takeIf { data.isNotEmpty() && data.size >= limit && it < count },
                itemsBefore = offset,
                itemsAfter = maxOf(0, count - nextOffset)
            )
        }

        if (invalid) LoadResult.Invalid() else page
    }

    override fun getRefreshKey(state: PagingState<Int, RowType>): Int? = state.anchorPosition?.let { anchorPosition ->
        maxOf(0, anchorPosition - state.config.initialLoadSize / 2)
    }

    override fun queryResultsChanged() {
        invalidate()
    }
}
