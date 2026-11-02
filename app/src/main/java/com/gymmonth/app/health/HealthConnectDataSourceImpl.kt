package com.gymmonth.app.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectDataSourceImpl @Inject constructor(
    private val clientProvider: HealthConnectClientProvider,
) : HealthConnectDataSource {

    override suspend fun listExerciseSessions(
        start: Instant,
        endExclusive: Instant,
    ): List<RawExerciseSession> {
        val client = clientProvider.requireClient()
        val out = mutableListOf<RawExerciseSession>()
        var pageToken: String? = null
        do {
            val page = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, endExclusive),
                    pageToken = pageToken,
                ),
            )
            for (rec in page.records) {
                out += rec.toRaw()
            }
            pageToken = page.pageToken
        } while (pageToken != null)
        return out
    }

    private fun ExerciseSessionRecord.toRaw(): RawExerciseSession =
        RawExerciseSession(
            id = metadata.id,
            exerciseType = exerciseType,
            start = startTime,
            end = endTime,
            dataOriginPackage = metadata.dataOrigin.packageName,
            title = title,
            notes = notes,
        )
}
