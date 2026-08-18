package sp.bvantur.inspektify.ktor.list.domain.usecase

import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import sp.bvantur.inspektify.ktor.list.di.KtorListModule.ktorListRepository
import sp.bvantur.inspektify.ktor.list.domain.model.NetworkTrafficListRow

internal interface GetAllNetworkTrafficDataUseCase {
    operator fun invoke(searchQuery: String, selectedTags: Set<String>): Flow<PagingData<NetworkTrafficListRow>>
}

internal class GetAllNetworkTrafficDataUseCaseImpl : GetAllNetworkTrafficDataUseCase {

    override fun invoke(searchQuery: String, selectedTags: Set<String>): Flow<PagingData<NetworkTrafficListRow>> =
        ktorListRepository.getNetworkTrafficItems(searchQuery, selectedTags).map { pagingData ->
            pagingData
                .map { item -> NetworkTrafficListRow.Traffic(item) }
                .insertSeparators { before, after ->
                    when {
                        after == null -> null
                        before == null || before.item.date != after.item.date ->
                            NetworkTrafficListRow.DateHeader(date = after.item.date, anchorId = after.item.id)

                        else -> null
                    }
                }
        }
}
