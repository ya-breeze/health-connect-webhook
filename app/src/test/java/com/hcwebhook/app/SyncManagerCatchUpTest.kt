package com.hcwebhook.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SyncManagerCatchUpTest {

    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private val thresholdMs = 48L * 3_600_000L
    private val sliceMs = 24L * 3_600_000L
    private val maxDays = 30L

    @Test
    fun automaticDeliveryRequiresEveryAttemptedWebhookToSucceed() {
        assertTrue(SyncManager.webhookBatchSucceeded(true, false, requireAllWebhookDeliveries = true))
        assertFalse(SyncManager.webhookBatchSucceeded(true, true, requireAllWebhookDeliveries = true))
        assertTrue(SyncManager.webhookBatchSucceeded(true, true, requireAllWebhookDeliveries = false))
        assertFalse(SyncManager.webhookBatchSucceeded(false, true, requireAllWebhookDeliveries = false))
    }

    @Test
    fun automaticRequestsNeverUpdateGeneralTimestampThemselves() {
        // Automatic orchestration (runAutomaticSync) owns both cursor writes;
        // performSync must never persist the general timestamp on its own for
        // either a normal or a replay automatic call, or a crash between
        // performSync's internal write and the orchestrator's automatic-cursor
        // write could seed a later run from a timestamp the automatic cursor
        // never actually reached.
        val normal = SyncManager.automaticSyncRequest(null, now, "auto", isReplaySlice = false)
        assertNull(normal.start)
        assertNull(normal.end)
        assertEquals(now, normal.defaultReadEnd)
        assertFalse(normal.updateLastSyncTime)
        assertTrue(normal.requireAllWebhookDeliveries)
        assertFalse(
            SyncManager.shouldUsePerTypeCursors(
                timeRangeDays = null,
                start = normal.start,
                end = normal.end,
                defaultReadEnd = normal.defaultReadEnd,
            ),
        )

        val replayStart = now.minusSeconds(24 * 3_600L)
        val normalFromCursor = SyncManager.automaticSyncRequest(
            replayStart,
            now,
            "auto",
            isReplaySlice = false,
        )
        assertEquals(replayStart, normalFromCursor.start)
        assertNull(normalFromCursor.end)
        assertEquals(now, normalFromCursor.defaultReadEnd)
        assertFalse(normalFromCursor.updateLastSyncTime)

        val replay = SyncManager.automaticSyncRequest(replayStart, now, "catchup", isReplaySlice = true)
        assertEquals(replayStart, replay.start)
        assertEquals(now, replay.end)
        assertNull(replay.defaultReadEnd)
        assertFalse(replay.updateLastSyncTime)
        assertTrue(replay.requireAllWebhookDeliveries)
    }

    @Test
    fun onlyUnboundedManualOrApiReadsUsePerTypeCursors() {
        assertTrue(
            SyncManager.shouldUsePerTypeCursors(
                timeRangeDays = null,
                start = null,
                end = null,
                defaultReadEnd = null,
            ),
        )
        assertFalse(
            SyncManager.shouldUsePerTypeCursors(
                timeRangeDays = null,
                start = null,
                end = null,
                defaultReadEnd = now,
            ),
        )
    }

    @Test
    fun freshInstallPersistsAutomaticBoundaryBeforeGeneralStatus() = runBlocking {
        val completion = now.plusSeconds(30).toEpochMilli()
        val events = mutableListOf<String>()

        val result = SyncManager.runAutomaticSync(
            automaticSyncMs = null,
            generalSyncMs = null,
            now = now,
            syncType = "auto",
            persistAutomaticSyncMs = { events.add("automatic:$it") },
            persistGeneralSyncMs = { events.add("general:$it") },
            completionTimeMs = { completion },
            sync = { start, end, type, isReplaySlice ->
                assertNull(start)
                assertEquals(now, end)
                assertEquals("auto", type)
                assertFalse(isReplaySlice)
                events.add("sync")
                Result.success(SyncResult.NoData)
            },
            pauseBetweenSlices = { error("normal sync must not pause") },
        )

        assertTrue(result.isSuccess)
        assertEquals(
            listOf("sync", "automatic:${now.toEpochMilli()}", "general:$completion"),
            events,
        )
    }

    @Test
    fun legacySeededInstallPersistsSeedBeforeSyncThenAutomaticBeforeGeneral() = runBlocking {
        val legacy = now.minusSeconds(24 * 3_600L).toEpochMilli()
        val completion = now.plusSeconds(30).toEpochMilli()
        val events = mutableListOf<String>()

        val result = SyncManager.runAutomaticSync(
            automaticSyncMs = null,
            generalSyncMs = legacy,
            now = now,
            syncType = "auto",
            persistAutomaticSyncMs = { events.add("automatic:$it") },
            persistGeneralSyncMs = { events.add("general:$it") },
            completionTimeMs = { completion },
            sync = { start, _, _, _ ->
                assertEquals(SyncManager.overlappedAutomaticReadStart(legacy, now), start)
                events.add("sync")
                Result.success(SyncResult.NoData)
            },
            pauseBetweenSlices = { error("normal sync must not pause") },
        )

        assertTrue(result.isSuccess)
        assertEquals(
            listOf("automatic:$legacy", "sync", "automatic:${now.toEpochMilli()}", "general:$completion"),
            events,
        )
    }

    @Test
    fun firstAutomaticRunPersistsLegacySeedBeforeSyncStarts() = runBlocking {
        val legacy = now.minusSeconds(24 * 3_600L).toEpochMilli()
        val events = mutableListOf<String>()

        val result = SyncManager.runAutomaticSync(
            automaticSyncMs = null,
            generalSyncMs = legacy,
            now = now,
            syncType = "auto",
            persistAutomaticSyncMs = { events.add("persist:$it") },
            persistGeneralSyncMs = { error("failed sync must not move general status") },
            completionTimeMs = { error("failed normal sync does not need completion time") },
            sync = { _, _, _, _ ->
                events.add("sync")
                Result.failure(IllegalStateException("delivery failed"))
            },
            pauseBetweenSlices = { error("normal sync must not pause") },
        )

        assertTrue(result.isFailure)
        assertEquals(listOf("persist:$legacy", "sync"), events)
    }

    @Test
    fun successfulNormalRunPersistsAutomaticBoundaryBeforeGeneralStatus() = runBlocking {
        val existing = now.minusSeconds(3 * 3_600L).toEpochMilli()
        val newerGeneral = now.minusSeconds(60).toEpochMilli()
        val completion = now.plusSeconds(30).toEpochMilli()
        val events = mutableListOf<String>()

        val result = SyncManager.runAutomaticSync(
            automaticSyncMs = existing,
            generalSyncMs = newerGeneral,
            now = now,
            syncType = "auto",
            persistAutomaticSyncMs = { events.add("automatic:$it") },
            persistGeneralSyncMs = { events.add("general:$it") },
            completionTimeMs = { completion },
            sync = { start, end, type, isReplaySlice ->
                // Reads from the automatic cursor (with its overlap applied)
                // even though a newer general (manual/API) activity timestamp
                // exists, so manual/API syncs cannot hide an automatic-delivery
                // gap.
                assertEquals(SyncManager.overlappedAutomaticReadStart(existing, now), start)
                assertEquals(now, end)
                assertEquals("auto", type)
                assertFalse(isReplaySlice)
                events.add("sync")
                Result.success(SyncResult.NoData)
            },
            pauseBetweenSlices = { error("normal sync must not pause") },
        )

        assertTrue(result.isSuccess)
        assertEquals(
            listOf("sync", "automatic:${now.toEpochMilli()}", "general:$completion"),
            events,
        )
    }

    @Test
    fun normalFailurePersistsNeitherAutomaticNorGeneralStatus() = runBlocking {
        val existing = now.minusSeconds(24 * 3_600L).toEpochMilli()
        val events = mutableListOf<String>()

        val result = SyncManager.runAutomaticSync(
            automaticSyncMs = existing,
            generalSyncMs = null,
            now = now,
            syncType = "auto",
            persistAutomaticSyncMs = { events.add("automatic:$it") },
            persistGeneralSyncMs = { events.add("general:$it") },
            completionTimeMs = { error("failed normal sync does not need completion time") },
            sync = { _, _, _, _ ->
                events.add("sync")
                Result.failure(IllegalStateException("delivery failed"))
            },
            pauseBetweenSlices = { error("normal sync must not pause") },
        )

        assertTrue(result.isFailure)
        assertEquals(listOf("sync"), events)
    }

    @Test
    fun completedReplayPersistsEverySliceBoundaryBeforeGeneralStatus() = runBlocking {
        val start = now.minusSeconds(54 * 3_600L).toEpochMilli()
        val completion = now.plusSeconds(5).toEpochMilli()
        val events = mutableListOf<String>()
        val readStarts = mutableListOf<Instant?>()

        val result = SyncManager.runAutomaticSync(
            automaticSyncMs = start,
            generalSyncMs = null,
            now = now,
            syncType = "auto",
            persistAutomaticSyncMs = { events.add("automatic:$it") },
            persistGeneralSyncMs = { events.add("general:$it") },
            completionTimeMs = { completion },
            sync = { sliceStart, _, type, isReplaySlice ->
                assertEquals("catchup", type)
                assertTrue(isReplaySlice)
                readStarts.add(sliceStart)
                events.add("sync")
                Result.success(SyncResult.NoData)
            },
            pauseBetweenSlices = { events.add("pause") },
        )

        assertTrue(result.isSuccess)
        assertEquals(
            listOf(
                "sync", "automatic:${start + sliceMs}", "pause",
                "sync", "automatic:${start + 2 * sliceMs}", "pause",
                "sync", "automatic:${now.toEpochMilli()}",
                "general:$completion",
            ),
            events,
        )
        // Each slice's read overlaps 24h before its own (already committed)
        // start, but the checkpoints above still land on the un-overlapped
        // slice boundaries.
        assertEquals(
            listOf(
                SyncManager.overlappedAutomaticReadStart(start, now),
                SyncManager.overlappedAutomaticReadStart(start + sliceMs, now),
                SyncManager.overlappedAutomaticReadStart(start + 2 * sliceMs, now),
            ),
            readStarts,
        )
    }

    @Test
    fun overlapRecoversRecordsTimestampedShortlyBeforeTheCommittedCursor() {
        val committed = now.minusSeconds(3 * 3_600L)
        val lateRecordTimestamp = committed.minusSeconds(23 * 3_600L)

        val readStart = SyncManager.overlappedAutomaticReadStart(committed.toEpochMilli(), now)

        assertTrue(readStart.isBefore(committed))
        assertFalse(readStart.isAfter(lateRecordTimestamp))
    }

    @Test
    fun overlapDoesNotChangeTheCheckpointedBoundary() {
        val committed = now.minusSeconds(3 * 3_600L).toEpochMilli()

        val readStart = SyncManager.overlappedAutomaticReadStart(committed, now)
        val nextCursor = SyncManager.nextAutomaticSyncCursor(committed, now.toEpochMilli(), syncSucceeded = true)

        assertNotEquals(readStart.toEpochMilli(), nextCursor)
        assertEquals(now.toEpochMilli(), nextCursor)
    }

    @Test
    fun overlapNeverReadsBeforeTheThirtyDayCatchUpHorizon() {
        val committed = now.minusSeconds(maxDays * 24 * 3_600L)

        val readStart = SyncManager.overlappedAutomaticReadStart(committed.toEpochMilli(), now)

        assertEquals(now.minusSeconds(maxDays * 24 * 3_600L), readStart)
    }

    @Test
    fun clampedReplayFirstSliceOverlapStillHonorsTheThirtyDayHorizon() = runBlocking {
        val tooOld = now.minusSeconds((maxDays + 10) * 24 * 3_600L).toEpochMilli()
        val readStarts = mutableListOf<Instant?>()

        val result = SyncManager.runAutomaticSync(
            automaticSyncMs = tooOld,
            generalSyncMs = null,
            now = now,
            syncType = "auto",
            persistAutomaticSyncMs = {},
            persistGeneralSyncMs = {},
            completionTimeMs = { now.toEpochMilli() },
            sync = { sliceStart, _, _, _ ->
                readStarts.add(sliceStart)
                Result.success(SyncResult.NoData)
            },
            pauseBetweenSlices = {},
        )

        assertTrue(result.isSuccess)
        val horizon = now.minusSeconds(maxDays * 24 * 3_600L)
        assertEquals(horizon, readStarts.first())
        assertTrue(readStarts.none { it!!.isBefore(horizon) })
    }

    @Test
    fun failedReplayPersistsOnlyCompletedSliceBoundariesAndNeverGeneralStatus() = runBlocking {
        val start = now.minusSeconds(3 * 24 * 3_600L).toEpochMilli()
        val firstBoundary = start + sliceMs
        val events = mutableListOf<String>()
        var callCount = 0

        val result = SyncManager.runAutomaticSync(
            automaticSyncMs = start,
            generalSyncMs = now.minusSeconds(3_600L).toEpochMilli(),
            now = now,
            syncType = "auto",
            persistAutomaticSyncMs = { events.add("automatic:$it") },
            persistGeneralSyncMs = { events.add("general:$it") },
            completionTimeMs = { error("failed replay does not need completion time") },
            sync = { _, _, type, isReplaySlice ->
                assertEquals("catchup", type)
                assertTrue(isReplaySlice)
                callCount++
                events.add("sync")
                if (callCount == 1) Result.success(SyncResult.NoData)
                else Result.failure(IllegalStateException("second slice failed"))
            },
            pauseBetweenSlices = { events.add("pause") },
        )

        assertTrue(result.isFailure)
        assertEquals(2, callCount)
        assertEquals(
            listOf("sync", "automatic:$firstBoundary", "pause", "sync"),
            events,
        )
    }

    @Test
    fun oldAutomaticCursorWinsOverNewerManualSyncTimestamp() {
        val automatic = now.minusSeconds(5 * 24 * 3_600L).toEpochMilli()
        val manual = now.minusSeconds(24 * 3_600L).toEpochMilli()

        val selected = SyncManager.selectAutomaticSyncCursor(automatic, manual)

        assertEquals(automatic, selected.timestampMs)
        assertFalse(selected.seededFromGeneral)
        assertNotNull(SyncManager.planCatchUpSlices(selected.timestampMs, now))
    }

    @Test
    fun oldAutomaticCursorWinsOverNewerApiSyncTimestamp() {
        val automatic = now.minusSeconds(4 * 24 * 3_600L).toEpochMilli()
        val api = now.minusSeconds(24 * 3_600L).toEpochMilli()

        val selected = SyncManager.selectAutomaticSyncCursor(automatic, api)

        assertEquals(automatic, selected.timestampMs)
        assertFalse(selected.seededFromGeneral)
        assertNotNull(SyncManager.planCatchUpSlices(selected.timestampMs, now))
    }

    @Test
    fun missingAutomaticCursorSeedsOnceFromLegacyGeneralTimestamp() {
        val legacy = now.minusSeconds(3 * 24 * 3_600L).toEpochMilli()

        val selected = SyncManager.selectAutomaticSyncCursor(null, legacy)

        assertEquals(legacy, selected.timestampMs)
        assertTrue(selected.seededFromGeneral)
    }

    @Test
    fun missingAutomaticAndGeneralTimestampsHasNoMigrationSeed() {
        val selected = SyncManager.selectAutomaticSyncCursor(null, null)

        assertNull(selected.timestampMs)
        assertFalse(selected.seededFromGeneral)
    }

    @Test
    fun successfulNormalRunInitializesAutomaticCursor() {
        val completedAt = now.toEpochMilli()

        assertEquals(
            completedAt,
            SyncManager.nextAutomaticSyncCursor(null, completedAt, syncSucceeded = true),
        )
    }

    @Test
    fun successfulNormalRunCorrectsFutureAutomaticCursor() {
        val futureCursor = now.plusSeconds(24 * 3_600L).toEpochMilli()
        val completedAt = now.toEpochMilli()

        assertEquals(
            completedAt,
            SyncManager.nextAutomaticSyncCursor(futureCursor, completedAt, syncSucceeded = true),
        )
    }

    @Test
    fun successfulSlicesCheckpointEachCompletedBoundary() {
        val start = now.minusSeconds(3 * 24 * 3_600L).toEpochMilli()
        val firstBoundary = start + sliceMs
        val secondBoundary = firstBoundary + sliceMs

        val afterFirst = SyncManager.nextAutomaticSyncCursor(start, firstBoundary, syncSucceeded = true)
        val afterSecond = SyncManager.nextAutomaticSyncCursor(afterFirst, secondBoundary, syncSucceeded = true)

        assertEquals(firstBoundary, afterFirst)
        assertEquals(secondBoundary, afterSecond)
    }

    @Test
    fun failedSliceLeavesCursorAtLastCompletedBoundary() {
        val start = now.minusSeconds(3 * 24 * 3_600L).toEpochMilli()
        val completedBoundary = start + sliceMs
        val failedBoundary = completedBoundary + sliceMs
        val afterSuccess = SyncManager.nextAutomaticSyncCursor(start, completedBoundary, syncSucceeded = true)

        val afterFailure = SyncManager.nextAutomaticSyncCursor(
            afterSuccess,
            failedBoundary,
            syncSucceeded = false,
        )

        assertEquals(completedBoundary, afterFailure)
    }

    @Test
    fun nullLastSyncReturnsNullAndFallsThroughToRegularSync() {
        assertNull(SyncManager.planCatchUpSlices(null, now))
    }

    @Test
    fun gapBelowThresholdReturnsNull() {
        val lastSync = now.minusMillis(thresholdMs - 1)
        assertNull(SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now))
    }

    @Test
    fun gapExactlyAtThresholdReturnsNull() {
        val lastSync = now.minusMillis(thresholdMs)
        assertNull(SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now))
    }

    @Test
    fun lastSyncInTheFutureReturnsNull() {
        val lastSync = now.plusMillis(thresholdMs)
        assertNull(SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now))
    }

    @Test
    fun gapOneMillisAboveThresholdReturnsCatchUpSlices() {
        val lastSync = now.minusMillis(thresholdMs + 1)
        val slices = SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now)
        assertNotNull(slices)
        assertTrue(slices!!.isNotEmpty())
    }

    @Test
    fun threeDayGapProducesThreeSlices() {
        val lastSync = now.minusMillis(3 * sliceMs)
        val slices = SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now)!!
        assertEquals(3, slices.size)
    }

    @Test
    fun partialLastSliceRoundsUp() {
        val lastSync = now.minusSeconds(54 * 3_600L)
        val slices = SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now)!!
        assertEquals(3, slices.size)
    }

    @Test
    fun firstSliceStartsAtAutomaticCursor() {
        val lastSync = now.minusMillis(5 * sliceMs)
        val slices = SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now)!!
        assertEquals(lastSync, slices.first().first)
    }

    @Test
    fun finalSliceEndsExactlyAtNow() {
        val lastSync = now.minusSeconds(54 * 3_600L)
        val slices = SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now)!!
        assertEquals(now, slices.last().second)
    }

    @Test
    fun slicesAreContiguousWithoutGapsOrOverlaps() {
        val lastSync = now.minusSeconds(70 * 3_600L)
        val slices = SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now)!!
        for (index in 1 until slices.size) {
            assertEquals(slices[index - 1].second, slices[index].first)
        }
    }

    @Test
    fun oldCursorIsClampedToThirtyDays() {
        val tooOld = now.minusSeconds((maxDays + 5) * 24 * 3_600L)
        val slices = SyncManager.planCatchUpSlices(tooOld.toEpochMilli(), now)!!
        assertEquals(now.minusSeconds(maxDays * 24 * 3_600L), slices.first().first)
    }

    @Test
    fun clampedCatchUpProducesThirtySlices() {
        val tooOld = now.minusSeconds((maxDays + 10) * 24 * 3_600L)
        val slices = SyncManager.planCatchUpSlices(tooOld.toEpochMilli(), now)!!
        assertEquals(maxDays.toInt(), slices.size)
    }
}
