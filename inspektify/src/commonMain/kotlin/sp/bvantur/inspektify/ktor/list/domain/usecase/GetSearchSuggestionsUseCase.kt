package sp.bvantur.inspektify.ktor.list.domain.usecase

import kotlinx.coroutines.flow.Flow
import sp.bvantur.inspektify.ktor.list.di.KtorListModule.ktorListRepository

internal interface GetSearchSuggestionsUseCase {
    operator fun invoke(): Flow<Set<String>>
}

internal class GetSearchSuggestionsUseCaseImpl : GetSearchSuggestionsUseCase {

    override fun invoke(): Flow<Set<String>> = ktorListRepository.getSearchSuggestions()
}
