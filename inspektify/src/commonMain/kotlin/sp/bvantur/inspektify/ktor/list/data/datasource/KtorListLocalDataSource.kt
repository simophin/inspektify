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

    fun getNetworkTrafficPagingSource(searchQuery: String): PagingSource<Int, GetNetworkTrafficPage> =
        OffsetQueryPagingSource(
            transacter = database.inspektifyDBQueries,
            context = dispatcherProvider.io,
            countQuery = { database.inspektifyDBQueries.countNetworkTraffic(searchQuery) },
            pageQuery = { limit, offset ->
                database.inspektifyDBQueries.getNetworkTrafficPage(searchQuery, limit, offset)
            }
        )

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

    fun getDistinctTags(): Flow<List<List<String>>> = database.inspektifyDBQueries
        .getDistinctTags()
        .asFlow()
        .mapToList(dispatcherProvider.default)
        .flowOn(dispatcherProvider.io)

    suspend fun removeAllNetworkTrafficData() {
        withContext(dispatcherProvider.io) {
            database.inspektifyDBQueries.removeAllNetworkTrafficData()
        }
    }

    fun getCurrentSessionTimestamp(): Long = cachedConfig.currentSessionTimeStamp

    fun getRetentionPolicy(): DataRetentionPolicy? = cachedConfig.retentionPolicy
}
