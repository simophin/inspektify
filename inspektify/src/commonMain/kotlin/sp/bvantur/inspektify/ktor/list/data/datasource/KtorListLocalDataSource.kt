package sp.bvantur.inspektify.ktor.list.data.datasource

import androidx.paging.PagingSource
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import sp.bvantur.inspektify.GetNetworkTrafficPage
import sp.bvantur.inspektify.ktor.DataRetentionPolicy
import sp.bvantur.inspektify.ktor.core.data.paging.OffsetQueryPagingSource
import sp.bvantur.inspektify.ktor.core.di.AppComponents.cachedConfig
import sp.bvantur.inspektify.ktor.core.di.AppComponents.database
import sp.bvantur.inspektify.ktor.core.di.AppComponents.dispatcherProvider

internal class KtorListLocalDataSource {

    fun getNetworkTrafficPagingSource(
        searchQuery: String,
        selectedTags: Set<String>
    ): PagingSource<Int, GetNetworkTrafficPage> {
        // Both queries have to be filtered exactly the same way, otherwise the paging source pairs a
        // count with rows it does not describe. The arguments are therefore resolved once here and
        // shared by both lambdas instead of being derived twice.
        val isTagFilterActive = if (selectedTags.isEmpty()) 0L else 1L
        // The tag branch is short circuited when nothing is selected, but the IN list still has to be
        // rendered, so a placeholder is bound instead of an empty one.
        val tagFilter = selectedTags.ifEmpty { setOf("") }

        return OffsetQueryPagingSource(
            transacter = database.inspektifyDBQueries,
            context = dispatcherProvider.io,
            countQuery = {
                database.inspektifyDBQueries.countNetworkTraffic(
                    isTagFilterActive = isTagFilterActive,
                    selectedTags = tagFilter,
                    searchQuery = searchQuery
                )
            },
            pageQuery = { limit, offset ->
                database.inspektifyDBQueries.getNetworkTrafficPage(
                    isTagFilterActive = isTagFilterActive,
                    selectedTags = tagFilter,
                    searchQuery = searchQuery,
                    limit = limit,
                    offset = offset
                )
            }
        )
    }

    fun getDistinctStatusCodes(): Flow<List<Long>> = database.inspektifyDBQueries
        .getDistinctStatusCodes()
        .asFlow()
        .mapToList(dispatcherProvider.default)
        .flowOn(dispatcherProvider.io)

    fun getDistinctMethods(): Flow<List<String>> = database.inspektifyDBQueries
        .getDistinctMethods()
        .asFlow()
        .mapToList(dispatcherProvider.default)
        .flowOn(dispatcherProvider.io)

    fun getDistinctTags(): Flow<List<String>> = database.inspektifyDBQueries
        .getDistinctTags()
        .asFlow()
        .mapToList(dispatcherProvider.default)
        .flowOn(dispatcherProvider.io)

    suspend fun removeAllNetworkTrafficData() {
        withContext(dispatcherProvider.io) {
            database.transaction {
                database.inspektifyDBQueries.removeAllNetworkTrafficTags()
                database.inspektifyDBQueries.removeAllNetworkTrafficData()
            }
        }
    }

    fun getCurrentSessionTimestamp(): Long = cachedConfig.currentSessionTimeStamp

    fun getRetentionPolicy(): DataRetentionPolicy? = cachedConfig.retentionPolicy
}
