package sp.bvantur.inspektify.ktor.list.presentation

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import sp.bvantur.inspektify.ktor.client.shared.Platform
import sp.bvantur.inspektify.ktor.core.presentation.SingleEventHandler
import sp.bvantur.inspektify.ktor.core.presentation.SingleEventHandlerImpl
import sp.bvantur.inspektify.ktor.core.presentation.ViewModelUserActionHandler
import sp.bvantur.inspektify.ktor.core.presentation.ViewStateViewModel
import sp.bvantur.inspektify.ktor.list.di.KtorListModule.getAllNetworkTrafficDataUseCase
import sp.bvantur.inspektify.ktor.list.di.KtorListModule.getAllTagsUseCase
import sp.bvantur.inspektify.ktor.list.di.KtorListModule.getCurrentSessionRetentionPolicy
import sp.bvantur.inspektify.ktor.list.di.KtorListModule.getSearchSuggestionsUseCase
import sp.bvantur.inspektify.ktor.list.di.KtorListModule.ktorListRepository
import sp.bvantur.inspektify.ktor.list.domain.model.NetworkTrafficListRow

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class KtorListVewModel :
    ViewStateViewModel<KtorListViewState>(
        initialViewState = KtorListViewState(retentionPolicyText = getCurrentSessionRetentionPolicy())
    ),
    SingleEventHandler<KtorListEvent> by SingleEventHandlerImpl(),
    ViewModelUserActionHandler<KtorListUserAction> {

    private val searchQueryFlow = MutableStateFlow("")
    private val selectedTagsFlow = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Paged traffic rows. Both the search query and the selected tags are applied in SQL, so
     * changing either one restarts paging from the database instead of filtering an in-memory list.
     */
    val networkTrafficPagingDataFlow: Flow<PagingData<NetworkTrafficListRow>> = combine(
        searchQueryFlow.debounce { searchQuery -> if (searchQuery.isEmpty()) 0L else SEARCH_DEBOUNCE },
        selectedTagsFlow
    ) { searchQuery, selectedTags -> searchQuery to selectedTags }
        .distinctUntilChanged()
        .flatMapLatest { (searchQuery, selectedTags) ->
            getAllNetworkTrafficDataUseCase(searchQuery, selectedTags)
        }
        .cachedIn(viewModelScope)

    override fun initialLoadData() {
        viewModelScope.launch {
            getSearchSuggestionsUseCase().collect { suggestions ->
                emitViewState { viewState ->
                    viewState.copy(suggestions = suggestions)
                }
            }
        }
        viewModelScope.launch {
            getAllTagsUseCase().collect { tags ->
                emitViewState { viewState ->
                    // Tags that disappeared from the database must not stay selected, otherwise the
                    // list would keep filtering on a chip that is no longer shown.
                    viewState.copy(allTags = tags, selectedTags = viewState.selectedTags intersect tags.toSet())
                }
                selectedTagsFlow.value = selectedTagsFlow.value intersect tags.toSet()
            }
        }
    }

    override fun onUserAction(userAction: KtorListUserAction) {
        when (userAction) {
            KtorListUserAction.OnRemoveAllNetworkTraffic -> onRemoveAllNetworkTraffic()
            KtorListUserAction.OnNavigateBack -> onNavigateBack()
            is KtorListUserAction.OnNetworkTrafficItemSelected -> onNetworkTrafficItemSelected(userAction.id)
            KtorListUserAction.OnStartSearch -> onStartSearch()
            KtorListUserAction.OnClearSearchQuery -> {
                onSearchQuery(viewStateFlow.value.searchQuery.copy(text = ""))
                viewModelScope.launch {
                    emitSingleEvent(KtorListEvent.MoveFocusOnSearch)
                }
            }
            is KtorListUserAction.OnSearchSuggestionQuery -> {
                onSearchQuery(viewStateFlow.value.searchQuery.copy(text = "${userAction.suggestion} "))
                emitViewState { viewState ->
                    val searchQuery = viewState.searchQuery
                    viewState.copy(searchQuery = searchQuery.copy(selection = TextRange(searchQuery.text.length)))
                }
                viewModelScope.launch {
                    emitSingleEvent(KtorListEvent.MoveFocusOnSearch)
                }
            }

            is KtorListUserAction.OnSearchQuery -> onSearchQuery(userAction.query)
            is KtorListUserAction.OnTagFilterToggled -> onTagFilterToggled(userAction.tag)
        }
    }

    private fun onTagFilterToggled(tag: String) {
        val selectedTags = viewStateFlow.value.selectedTags
        val newSelectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag

        // Feeds the same flatMapLatest as the search query, so a new Pager replaces the current one.
        selectedTagsFlow.value = newSelectedTags
        emitViewState { viewState ->
            viewState.copy(selectedTags = newSelectedTags)
        }
    }

    private fun onRemoveAllNetworkTraffic() {
        viewModelScope.launch {
            ktorListRepository.removeAllNetworkTrafficData()
        }
    }

    private fun onNetworkTrafficItemSelected(id: Long) {
        viewModelScope.launch {
            emitSingleEvent(KtorListEvent.ToNetworkDetails(id))
        }
    }

    private fun onNavigateBack() {
        if (viewStateFlow.value.isSearching) {
            viewModelScope.launch {
                emitSingleEvent(KtorListEvent.RemoveFocusFromSearch)
                searchQueryFlow.value = ""
                emitViewState { viewState ->
                    viewState.copy(
                        isSearching = false,
                        searchQuery = TextFieldValue(""),
                        showNavigationBackAction = !Platform.getTargetType().isDesktop()
                    )
                }
            }
        } else {
            Platform.closeInspektifyWindow()
        }
    }

    private fun onStartSearch() {
        viewModelScope.launch {
            emitViewState { viewState ->
                viewState.copy(
                    isSearching = true,
                    showNavigationBackAction = true
                )
            }
            delay(KEYBOARD_DELAY)
            emitSingleEvent(KtorListEvent.MoveFocusOnSearch)
        }
    }

    private fun onSearchQuery(query: TextFieldValue) {
        searchQueryFlow.value = query.text.trim()

        emitViewState { viewState ->
            viewState.copy(searchQuery = query)
        }
    }

    companion object {
        private const val KEYBOARD_DELAY = 200L
        private const val SEARCH_DEBOUNCE = 250L
    }
}
