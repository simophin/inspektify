package sp.bvantur.inspektify.ktor.list.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import sp.bvantur.inspektify.ktor.DataRetentionPolicy
import sp.bvantur.inspektify.ktor.list.data.mapper.NetworkTrafficDataLocalMapper.toDomainModel
import sp.bvantur.inspektify.ktor.list.di.KtorListModule.ktorListLocalDataSource
import sp.bvantur.inspektify.ktor.list.domain.KtorListRepository
import sp.bvantur.inspektify.ktor.list.domain.model.NetworkTrafficListItem

private const val PAGE_SIZE = 50
private const val PREFETCH_DISTANCE = 25
private const val INITIAL_LOAD_SIZE = 100

// Pages that are far enough off screen are dropped again, so a long lived session with thousands
// of requests never keeps more than this many rows in memory.
private const val MAX_ITEMS_IN_MEMORY = 200

internal class KtorListRepositoryImpl : KtorListRepository {

    override fun getNetworkTrafficItems(
        searchQuery: String,
        selectedTags: Set<String>
    ): Flow<PagingData<NetworkTrafficListItem>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            prefetchDistance = PREFETCH_DISTANCE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = false,
            maxSize = MAX_ITEMS_IN_MEMORY
        ),
        pagingSourceFactory = {
            ktorListLocalDataSource.getNetworkTrafficPagingSource(searchQuery, selectedTags)
        }
    ).flow.map { pagingData ->
        val currentSessionTimestamp = ktorListLocalDataSource.getCurrentSessionTimestamp()
        pagingData.map { singleItem -> singleItem.toDomainModel(currentSessionTimestamp) }
    }

    override fun getSearchSuggestions(): Flow<Set<String>> = combine(
        ktorListLocalDataSource.getDistinctStatusCodes(),
        ktorListLocalDataSource.getDistinctMethods(),
        getAllTags()
    ) { statusCodes, methods, tags ->
        val statusCodeSuggestions = statusCodes.map { statusCode -> statusCode.toString() }
        val methodSuggestions = methods.filter { method -> method.isNotBlank() }

        (statusCodeSuggestions + methodSuggestions + tags).toSet()
    }

    // Shared by the search suggestions and the tag filter chips so both always show the same tags.
    override fun getAllTags(): Flow<List<String>> = ktorListLocalDataSource.getDistinctTags()
        .map { tags -> tags.filter { tag -> tag.isNotBlank() } }

    override suspend fun removeAllNetworkTrafficData() {
        ktorListLocalDataSource.removeAllNetworkTrafficData()
    }

    override fun getRetentionPolicy(): DataRetentionPolicy? = ktorListLocalDataSource.getRetentionPolicy()
}
