package sp.bvantur.inspektify.ktor.list.domain.usecase

import kotlinx.coroutines.flow.Flow
import sp.bvantur.inspektify.ktor.list.di.KtorListModule.ktorListRepository

internal interface GetAllTagsUseCase {
    operator fun invoke(): Flow<List<String>>
}

internal class GetAllTagsUseCaseImpl : GetAllTagsUseCase {

    override fun invoke(): Flow<List<String>> = ktorListRepository.getAllTags()
}
