@file:Suppress("TooManyFunctions")

package sp.bvantur.inspektify.ktor.core.data.utils.extensions

// TODO move this to ui layer?

import inspektifyroot.inspektify.generated.resources.Res
import inspektifyroot.inspektify.generated.resources.img_http_icon
import inspektifyroot.inspektify.generated.resources.img_https_icon
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import sp.bvantur.inspektify.GetNetworkTrafficPage
import sp.bvantur.inspektify.ktor.core.domain.utils.ByteSizeUtils
import sp.bvantur.inspektify.ktor.core.domain.utils.DateTimeUtils
import sp.bvantur.inspektify.ktor.core.domain.utils.KtorPresentationConstants
import sp.bvantur.inspektify.ktor.list.domain.model.StatusCode
import sp.bvantur.inspektify.ktor.list.domain.model.StatusColor
import kotlin.time.ExperimentalTime

/**
 * ASCII unit separator. Used instead of a comma so that tags containing punctuation survive the
 * round trip through `group_concat`.
 */
internal const val TAG_DELIMITER: String = "\u001F"

internal fun GetNetworkTrafficPage.getPresentationStatusCode(): StatusCode {
    responseStatus
        ?: return StatusCode(statusCode = KtorPresentationConstants.MISSING_DATA, statusColor = StatusColor.ORANGE)

    return StatusCode(
        statusCode = responseStatus.toString(),
        statusColor = if (responseStatus in 200L..299L) {
            StatusColor.GREEN
        } else {
            StatusColor.RED
        }
    )
}

internal fun GetNetworkTrafficPage.getMethodWithPath(): String {
    if (method == null) return path ?: ""
    if (path == null) return "$method"

    return "$method $path"
}

/**
 * Tags of a paged row arrive as a single [TAG_DELIMITER] separated string, concatenated by the page
 * query out of [sp.bvantur.inspektify.NetworkTrafficTagLocal] rows. `group_concat` gives no ordering
 * guarantee, so the tags are sorted here to keep the list rows stable between loads.
 */
internal fun GetNetworkTrafficPage.getTags(): List<String> = tags
    .split(TAG_DELIMITER)
    .filter { tag -> tag.isNotBlank() }
    .sorted()

internal fun GetNetworkTrafficPage.getHost(): String = host ?: ""

internal fun GetNetworkTrafficPage.getMethod(): String = method ?: ""

@OptIn(ExperimentalTime::class)
internal fun GetNetworkTrafficPage.getTime(systemTimeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    requestTimestamp ?: return KtorPresentationConstants.MISSING_DATA

    val instant = Instant.fromEpochMilliseconds(requestTimestamp)
    val localDateTime = instant.toLocalDateTime(systemTimeZone)

    return DateTimeUtils.toTimeString(localDateTime)
}

internal fun GetNetworkTrafficPage.getDuration(): String {
    if (responseTimestamp == null || requestTimestamp == null) return KtorPresentationConstants.MISSING_DATA

    return DateTimeUtils.toTextWithTimeUnit(responseTimestamp - requestTimestamp)
}

internal fun GetNetworkTrafficPage.getSize(): String {
    var allSize = 0L
    if (responsePayloadSize != null) {
        allSize += responsePayloadSize
    }
    if (responseHeadersSize != null) {
        allSize += responseHeadersSize
    }
    if (requestPayloadSize != null) {
        allSize += requestPayloadSize
    }
    if (requestHeadersSize != null) {
        allSize += requestHeadersSize
    }

    return ByteSizeUtils.toTextWithByteUnit(allSize)
}

internal fun GetNetworkTrafficPage.getHostImage(): DrawableResource = if (protocol == "https") {
    Res.drawable.img_https_icon
} else {
    Res.drawable.img_http_icon
}

@OptIn(ExperimentalTime::class)
internal fun GetNetworkTrafficPage.getDate(systemTimeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val instant = Instant.fromEpochMilliseconds(requestTimestamp ?: 0L)

    return DateTimeUtils.formatDate(instant.toLocalDateTime(systemTimeZone).date)
}

internal fun GetNetworkTrafficPage.isCompleted(): Boolean = responseStatus != null

@Suppress("UnnecessaryParentheses")
internal fun GetNetworkTrafficPage.isFromActiveSession(sessionTimestamp: Long): Boolean =
    (requestTimestamp ?: 0L) >= sessionTimestamp
