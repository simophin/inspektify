package sp.bvantur.inspektify.ktor.list.domain.model

/**
 * A single row of the paged traffic list. Date headers are injected between items whenever the
 * date changes, so they arrive through the same paged stream as the traffic items themselves.
 */
internal sealed interface NetworkTrafficListRow {
    /**
     * [anchorId] is the id of the item right below the header. It only exists to give every header
     * a stable and unique list key, even if the same date were to show up more than once.
     */
    data class DateHeader(val date: String, val anchorId: Long) : NetworkTrafficListRow
    data class Traffic(val item: NetworkTrafficListItem) : NetworkTrafficListRow
}
