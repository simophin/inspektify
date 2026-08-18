package sp.bvantur.inspektify.ktor.list.presentation

import androidx.compose.ui.text.input.TextFieldValue
import sp.bvantur.inspektify.ktor.client.shared.Platform
import sp.bvantur.inspektify.ktor.core.presentation.ViewState

internal data class KtorListViewState(
    val suggestions: Set<String> = emptySet(),
    val retentionPolicyText: String = "",
    val isSearching: Boolean = false,
    val searchQuery: TextFieldValue = TextFieldValue(""),
    val showNavigationBackAction: Boolean = !Platform.getTargetType().isDesktop()
) : ViewState
