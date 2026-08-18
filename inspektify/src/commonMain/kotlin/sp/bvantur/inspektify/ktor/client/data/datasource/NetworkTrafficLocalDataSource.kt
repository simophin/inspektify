package sp.bvantur.inspektify.ktor.client.data.datasource

import kotlinx.coroutines.withContext
import sp.bvantur.inspektify.NetworkTrafficDataLocal
import sp.bvantur.inspektify.ktor.client.domain.model.NetworkTraffic
import sp.bvantur.inspektify.ktor.core.di.AppComponents.database
import sp.bvantur.inspektify.ktor.core.di.AppComponents.dispatcherProvider

internal class NetworkTrafficLocalDataSource {
    suspend fun saveNetworkTrafficData(networkTraffic: NetworkTraffic) {
        withContext(dispatcherProvider.default) {
            database.transaction {
                // Insert followed by update rather than INSERT OR REPLACE: REPLACE resolves the
                // conflict by deleting the existing row, which cascades into NetworkTrafficTagLocal
                // and would drop the tags of the request when the response phase saves it again.
                insertOrIgnoreNetworkTraffic(networkTraffic)
                updateNetworkTraffic(networkTraffic)

                networkTraffic.tags?.forEach { tag ->
                    database.inspektifyDBQueries.insertNetworkTrafficTag(
                        networkTrafficId = networkTraffic.id,
                        tag = tag
                    )
                }
            }
        }
    }

    private fun insertOrIgnoreNetworkTraffic(networkTraffic: NetworkTraffic) {
        database.inspektifyDBQueries.insertOrIgnoreNetworkTraffic(
            id = networkTraffic.id,
            sessionId = networkTraffic.sessionId,
            method = networkTraffic.method,
            url = networkTraffic.url,
            host = networkTraffic.host,
            path = networkTraffic.path,
            protocol = networkTraffic.protocol,
            requestTimestamp = networkTraffic.requestTimestamp,
            requestHeaders = networkTraffic.requestHeaders,
            requestPayload = networkTraffic.requestPayload,
            requestContentType = networkTraffic.requestContentType,
            requestPayloadSize = networkTraffic.requestPayloadSize,
            requestHeadersSize = networkTraffic.requestHeadersSize,
            responseTimestamp = networkTraffic.responseTimestamp,
            responseStatus = networkTraffic.responseStatus?.toLong(),
            responseStatusDescription = networkTraffic.responseStatusDescription,
            responseHeaders = networkTraffic.responseHeaders,
            responsePayload = networkTraffic.responsePayload,
            responseContentType = networkTraffic.responseContentType,
            responsePayloadSize = networkTraffic.responsePayloadSize,
            responseHeadersSize = networkTraffic.responseHeadersSize?.toLong(),
            tookDurationInMs = networkTraffic.tookDurationInMs
        )
    }

    private fun updateNetworkTraffic(networkTraffic: NetworkTraffic) {
        database.inspektifyDBQueries.updateNetworkTraffic(
            id = networkTraffic.id,
            sessionId = networkTraffic.sessionId,
            method = networkTraffic.method,
            url = networkTraffic.url,
            host = networkTraffic.host,
            path = networkTraffic.path,
            protocol = networkTraffic.protocol,
            requestTimestamp = networkTraffic.requestTimestamp,
            requestHeaders = networkTraffic.requestHeaders,
            requestPayload = networkTraffic.requestPayload,
            requestContentType = networkTraffic.requestContentType,
            requestPayloadSize = networkTraffic.requestPayloadSize,
            requestHeadersSize = networkTraffic.requestHeadersSize,
            responseTimestamp = networkTraffic.responseTimestamp,
            responseStatus = networkTraffic.responseStatus?.toLong(),
            responseStatusDescription = networkTraffic.responseStatusDescription,
            responseHeaders = networkTraffic.responseHeaders,
            responsePayload = networkTraffic.responsePayload,
            responseContentType = networkTraffic.responseContentType,
            responsePayloadSize = networkTraffic.responsePayloadSize,
            responseHeadersSize = networkTraffic.responseHeadersSize?.toLong(),
            tookDurationInMs = networkTraffic.tookDurationInMs
        )
    }

    suspend fun getNetworkTrafficData(id: Long): NetworkTrafficDataLocal? = withContext(dispatcherProvider.io) {
        database.inspektifyDBQueries.getNetworkTrafficById(
            id
        ).executeAsOneOrNull()
    }

    suspend fun getNetworkTrafficTags(id: Long): List<String> = withContext(dispatcherProvider.io) {
        database.inspektifyDBQueries.getTagsByNetworkTrafficId(id).executeAsList()
    }

    // The tag deletes below are redundant while foreign keys are on, since ON DELETE CASCADE already
    // removes them. They are kept as defence in depth: the cascade can only be verified on the JVM
    // here, and a connection that somehow comes up without the pragma would otherwise leak tag rows.
    suspend fun removeNetworkTrafficOlderThan(cutoffTimestamp: Long) {
        withContext(dispatcherProvider.io) {
            database.transaction {
                database.inspektifyDBQueries.removeNetworkTrafficTagsOlderThan(cutoffTimestamp)
                database.inspektifyDBQueries.removeNetworkTrafficOlderThan(cutoffTimestamp)
            }
        }
    }

    suspend fun getAllSessionsIds(): List<Long> = withContext(dispatcherProvider.io) {
        database.inspektifyDBQueries.getDistinctSessionIds().executeAsList()
    }

    fun removeNetworkTrafficWithNextSessionIds(sessionsToRemove: List<Long>) {
        database.transaction {
            sessionsToRemove.forEach { sessionId ->
                database.inspektifyDBQueries.removeTagRowsBySessionId(sessionId)
                database.inspektifyDBQueries.removeRowsBySessionId(sessionId)
            }
        }
    }
}
