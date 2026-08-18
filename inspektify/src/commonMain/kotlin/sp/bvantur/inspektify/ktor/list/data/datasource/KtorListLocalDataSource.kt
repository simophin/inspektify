package sp.bvantur.inspektify.ktor.list.data.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import sp.bvantur.inspektify.GetAllNetworkTrafficForList
import sp.bvantur.inspektify.ktor.DataRetentionPolicy
import sp.bvantur.inspektify.ktor.core.di.AppComponents.cachedConfig
import sp.bvantur.inspektify.ktor.core.di.AppComponents.database
import sp.bvantur.inspektify.ktor.core.di.AppComponents.dispatcherProvider

internal class KtorListLocalDataSource {

    fun getAllNetworkTrafficData(): Flow<List<GetAllNetworkTrafficForList>> = database.inspektifyDBQueries
        .getAllNetworkTrafficForList()
        .asFlow()
        .mapToList(dispatcherProvider.default).flowOn(dispatcherProvider.io)

    suspend fun removeAllNetworkTrafficData() {
        withContext(dispatcherProvider.io) {
            database.inspektifyDBQueries.removeAllNetworkTrafficData()
        }
    }

    fun getCurrentSessionTimestamp(): Long = cachedConfig.currentSessionTimeStamp

    fun getRetentionPolicy(): DataRetentionPolicy? = cachedConfig.retentionPolicy
}
