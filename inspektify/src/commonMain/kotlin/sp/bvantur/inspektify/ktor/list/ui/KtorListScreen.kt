package sp.bvantur.inspektify.ktor.list.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import org.jetbrains.compose.resources.painterResource
import sp.bvantur.inspektify.ktor.core.ui.theme.disabled
import sp.bvantur.inspektify.ktor.core.ui.utils.ColorUtils
import sp.bvantur.inspektify.ktor.list.domain.model.NetworkTrafficListItem
import sp.bvantur.inspektify.ktor.list.domain.model.NetworkTrafficListRow
import sp.bvantur.inspektify.ktor.list.presentation.KtorListUserAction
import sp.bvantur.inspektify.ktor.list.presentation.KtorListViewState

private const val DATE_HEADER_CONTENT_TYPE = "date-header"
private const val TRAFFIC_CONTENT_TYPE = "traffic"

@Composable
internal fun NetworkPageContent(
    viewState: KtorListViewState,
    networkTrafficItems: LazyPagingItems<NetworkTrafficListRow>,
    onUserAction: (KtorListUserAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        val isEmpty = networkTrafficItems.itemCount == 0 &&
            networkTrafficItems.loadState.refresh !is LoadState.Loading

        // Kept outside of the empty check on purpose: a tag filter that matches nothing still has to
        // be visible so it can be switched off again.
        if (viewState.allTags.isNotEmpty()) {
            NetworkTrafficTagFilterRow(
                allTags = viewState.allTags,
                selectedTags = viewState.selectedTags,
                onUserAction = onUserAction
            )
        }

        if (isEmpty) {
            Text(
                text = "No items",
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(
                    count = networkTrafficItems.itemCount,
                    key = networkTrafficItems.itemKey { row ->
                        when (row) {
                            is NetworkTrafficListRow.DateHeader -> "date-header-${row.anchorId}"
                            is NetworkTrafficListRow.Traffic -> row.item.id
                        }
                    },
                    contentType = networkTrafficItems.itemContentType { row ->
                        when (row) {
                            is NetworkTrafficListRow.DateHeader -> DATE_HEADER_CONTENT_TYPE
                            is NetworkTrafficListRow.Traffic -> TRAFFIC_CONTENT_TYPE
                        }
                    }
                ) { index ->
                    when (val row = networkTrafficItems[index]) {
                        is NetworkTrafficListRow.DateHeader -> NetworkTrafficDateHeader(date = row.date)
                        is NetworkTrafficListRow.Traffic -> {
                            val networkTrafficItem = row.item

                            NetworkTrafficItem(
                                item = networkTrafficItem,
                                modifier = Modifier.clickable {
                                    if (!networkTrafficItem.isCompleted) return@clickable

                                    onUserAction(KtorListUserAction.OnNetworkTrafficItemSelected(networkTrafficItem.id))
                                }
                            )
                        }

                        null -> Unit
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondary)) {
                Text(
                    text = viewState.retentionPolicyText,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NetworkTrafficTagFilterRow(
    allTags: List<String>,
    selectedTags: Set<String>,
    onUserAction: (KtorListUserAction) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        allTags.forEach { tag ->
            FilterChip(
                selected = tag in selectedTags,
                onClick = { onUserAction(KtorListUserAction.OnTagFilterToggled(tag)) },
                label = {
                    Text(text = tag)
                }
            )
        }
    }
}

@Composable
internal fun NetworkTrafficDateHeader(date: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondary)
    ) {
        Text(
            text = date,
            color = MaterialTheme.colorScheme.onSecondary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(8.dp).align(Alignment.Center)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NetworkTrafficItem(item: NetworkTrafficListItem, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth()
            .background(
                if (item.isCurrentSession) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.disabled
            )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                modifier = Modifier.padding(all = 16.dp),
                text = item.statusCode,
                color = ColorUtils.statusColorToComposableColor(item.statusColor),
                fontWeight = FontWeight.Bold
            )
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(
                    text = item.methodWithPath,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp)
                )

                if (item.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item.tags.forEach { tag -> NetworkTrafficTag(tag) }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.showSslIcon) {
                        Image(
                            modifier = Modifier.size(12.dp),
                            painter = painterResource(item.hostImage),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                        )
                    }
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        text = item.host,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 16.dp, bottom = 8.dp)) {
                    TextWithIcon(
                        text = item.time,
                        icon = Icons.Outlined.Timer,
                        modifier = Modifier.weight(1f)
                    )
                    TextWithIcon(
                        text = item.duration,
                        icon = Icons.Outlined.HourglassBottom,
                        modifier = Modifier.weight(1f)
                    )
                    TextWithIcon(
                        text = item.size,
                        icon = Icons.Outlined.Storage,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().height(1.dp).padding(bottom = 16.dp)
                )
            }
        }

        if (!item.isCompleted) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
internal fun NetworkTrafficTag(tag: String, modifier: Modifier = Modifier) {
    Text(
        text = tag,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
internal fun TextWithIcon(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = "Size Icon",
            modifier = Modifier.padding(end = 4.dp).size(12.dp)
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
