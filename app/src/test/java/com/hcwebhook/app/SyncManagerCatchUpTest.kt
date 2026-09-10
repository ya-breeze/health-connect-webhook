package com.hcwebhook.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun automaticRequestsUseCapturedBoundaryAndRequireAllWebhooks() {
        val normal = SyncManager.automaticSyncRequest(null, now, "auto", isReplaySlice = false)
        assertNull(normal.start)
        assertNull(normal.end)
        assertEquals(now, normal.defaultReadEnd)
        assertTrue(normal.updateLastSyncTime)
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
        assertTrue(normalFromCursor.updateLastSyncTime)

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
    fun successfulNormalRunPersistsEntryBoundaryAfterSyncCompletes() = runBlocking {
        val persistedAutomatic = mutableListOf<Long>()
        val completion = now.plusSeconds(30).toEpochMilli()

        val result = SyncManager.runAutomaticSync(
            automaticSyncMs = null,
            generalSyncMs = null,
            now = now,
            syncType = "auto",
            persistAutomaticSyncMs = persistedAutomatic::add,
            persistGeneralSyncMs = { error("normal sync owns the general timestamp") },
            completionTimeMs = { completion },
            sync = { start, end, type, isReplaySlice ->
                assertNull(start)
                assertEquals(now, end)
                assertEquals("auto", type)
                assertFalse(isReplaySlice)
                Result.success(SyncResult.NoData)
            },
            pauseBetweenSlices = { error("normal sync must not pause") },
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf(now.toEpochMilli()), persistedAutomatic)
    }

    @Test
    fun failedNormalRunDoesNotAdvanceAutomaticProgress() = runBlocking {
        val existing = now.minusSeconds(24 * 3_600L).toEpochMilli()
        val persistedAutomatic = mutableListOf<Long>()

        val result = SyncManager.runAutomaticSync(
            automaticSyncMs = existing,
            generalSyncMs = null,
            now = now,
            syncType = "auto",
            persistAutomaticSyncMs = persistedAutomatic::add,
            persistGeneralSyncMs = { error("failed sync must not move general status") },
            completionTimeMs = { error("failed normal sync does not need completion time") },
            sync = { _, _, _, _ -> Result.failure(IllegalStateException("delivery failed")) },
            pauseBetweenSlices = { error("normal sync must not pause") },
        )

        assertTrue(result.isFailure)
        assertTrue(persistedAutomatic.isEmpty())
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
            persistGeneralSyncMs = { error("normal sync owns the general timestamp") },
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
    fun failedReplayPersistsOnlyCompletedSliceBoundary() = runBlocking {
        val start = now.minusSeconds(3 * 24 * 3_600L).toEpochMilli()
        val firstBoundary = start + sliceMs
        val persistedAutomatic = mutableListOf<Long>()
        val calls = mutableListOf<Pair<Instant?, Instant?>>()

        val result = SyncManager.runAutomaticSync(
            automaticSyncMs = start,
            generalSyncMs = now.minusSeconds(3_600L).toEpochMilli(),
            now = now,
            syncType = "auto",
            persistAutomaticSyncMs = persistedAutomatic::add,
            persistGeneralSyncMs = { error("failed replay must not move general status") },
            completionTimeMs = { error("failed replay does not need completion time") },
            sync = { sliceStart, sliceEnd, type, isReplaySlice ->
                assertEquals("catchup", type)
                assertTrue(isReplaySlice)
                calls.add(sliceStart to sliceEnd)
                if (calls.size == 1) Result.success(SyncResult.NoData)
                else Result.failure(IllegalStateException("second slice failed"))
            },
            pauseBetweenSlices = {},
        )

        assertTrue(result.isFailure)
        assertEquals(2, calls.size)
        assertEquals(listOf(firstBoundary), persistedAutomatic)
    }

    @Test
    fun completedReplayCheckpointsEverySliceThenUpdatesGeneralStatusOnce() = runBlocking {
        val start = now.minusSeconds(54 * 3_600L).toEpochMilli()
        val completion = now.plusSeconds(5).toEpochMilli()
        val persistedAutomatic = mutableListOf<Long>()
        val persistedGeneral = mutableListOf<Long>()
        var pauses = 0

        val result = SyncManager.runAutomaticSync(
            automaticSyncMs = start,
            generalSyncMs = null,
            now = now,
            syncType = "auto",
            persistAutomaticSyncMs = persistedAutomatic::add,
            persistGeneralSyncMs = persistedGeneral::add,
            completionTimeMs = { completion },
            sync = { _, _, type, isReplaySlice ->
                assertEquals("catchup", type)
                assertTrue(isReplaySlice)
                Result.success(SyncResult.NoData)
            },
            pauseBetweenSlices = { pauses++ },
        )

        assertTrue(result.isSuccess)
        assertEquals(
            listOf(start + sliceMs, start + 2 * sliceMs, now.toEpochMilli()),
            persistedAutomatic,
        )
        assertEquals(listOf(completion), persistedGeneral)
        assertEquals(2, pauses)
    }

    @Test
    fun normalRunReadsFromAutomaticCursorDespiteNewerGeneralActivity() = runBlocking {
        val automatic = now.minusSeconds(24 * 3_600L).toEpochMilli()
        val newerGeneral = now.minusSeconds(3_600L).toEpochMilli()

        val result = SyncManager.runAutomaticSync(
            automaticSyncMs = automatic,
            generalSyncMs = newerGeneral,
            now = now,
            syncType = "auto",
            persistAutomaticSyncMs = {},
            persistGeneralSyncMs = { error("normal sync owns the general timestamp") },
            completionTimeMs = { error("normal sync does not need completion time") },
            sync = { start, end, type, isReplaySlice ->
                assertEquals(Instant.ofEpochMilli(automatic), start)
                assertEquals(now, end)
                assertEquals("auto", type)
                assertFalse(isReplaySlice)
                Result.success(SyncResult.NoData)
            },
            pauseBetweenSlices = { error("normal sync must not pause") },
        )

        assertTrue(result.isSuccess)
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
