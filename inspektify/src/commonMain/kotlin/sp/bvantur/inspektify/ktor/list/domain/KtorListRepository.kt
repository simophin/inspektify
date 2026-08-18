package sp.bvantur.inspektify.ktor.list.domain

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import sp.bvantur.inspektify.ktor.DataRetentionPolicy
import sp.bvantur.inspektify.ktor.list.domain.model.NetworkTrafficListItem

internal interface KtorListRepository {

    fun getNetworkTrafficItems(searchQuery: String): Flow<PagingData<NetworkTrafficListItem>>

    fun getSearchSuggestions(): Flow<Set<String>>

    suspend fun removeAllNetworkTrafficData()

    fun getRetentionPolicy(): DataRetentionPolicy?
}
