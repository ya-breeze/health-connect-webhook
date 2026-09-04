package com.hcwebhook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for the catch-up slice-planning logic introduced in performSyncWithCatchUp.
 *
 * SyncManager.planCatchUpSlices is a pure function (no Android dependencies) that decides
 * whether a gap in sync history is large enough to warrant catch-up replay, and if so
 * returns the ordered list of time-range slices to process. These tests verify:
 *   - the gap-detection threshold (falls through to normal sync when gap is small)
 *   - correct number of slices for various gap sizes
 *   - slice contiguity (no gaps, no overlaps)
 *   - the final slice is clamped to `now` (never extends into the future)
 *   - old lastSyncMs beyond MAX_CATCHUP_DAYS is clamped to the earliest allowed start
 */
class SyncManagerCatchUpTest {

    // Fixed reference point so tests are deterministic.
    private val now = Instant.parse("2026-06-01T12:00:00Z")

    // These mirror the companion constants.  If the constants change, tests fail loudly.
    private val thresholdMs = 48L * 3_600_000L  // GAP_THRESHOLD_HOURS (= LOOKBACK_HOURS)
    private val sliceMs     = 24L * 3_600_000L  // SLICE_HOURS
    private val maxDays     = 30L               // MAX_CATCHUP_DAYS

    // --- Threshold / no-catch-up cases ---

    @Test
    fun nullLastSync_returnsNull_fallsThroughToRegularSync() {
        assertNull(SyncManager.planCatchUpSlices(null, now))
    }

    @Test
    fun gapBelowThreshold_returnsNull() {
        val lastSync = now.minusMillis(thresholdMs - 1)
        assertNull(SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now))
    }

    @Test
    fun gapExactlyAtThreshold_returnsNull() {
        // Equal to the threshold is NOT a catch-up; the condition is strictly greater.
        val lastSync = now.minusMillis(thresholdMs)
        assertNull(SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now))
    }

    @Test
    fun lastSyncInTheFuture_returnsNull() {
        // A clock skew or out-of-order call could put the watermark ahead of `now`;
        // the gap is then negative, which must fall through like any small gap.
        val lastSync = now.plusMillis(thresholdMs)
        assertNull(SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now))
    }

    @Test
    fun gapOneMillisAboveThreshold_returnsCatchUpSlices() {
        val lastSync = now.minusMillis(thresholdMs + 1)
        val slices = SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now)
        assertNotNull(slices)
        assertTrue("catch-up must produce at least one slice", slices!!.isNotEmpty())
    }

    // --- Slice count ---

    @Test
    fun threeDayGap_producesThreeSlices() {
        // Exactly 3 × 24 h → 3 full slices.
        val lastSync = now.minusMillis(3 * sliceMs)
        val slices = SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now)!!
        assertEquals(3, slices.size)
    }

    @Test
    fun fiveDayGap_producesFiveSlices() {
        val lastSync = now.minusMillis(5 * sliceMs)
        val slices = SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now)!!
        assertEquals(5, slices.size)
    }

    @Test
    fun partialLastSlice_roundsUpToOneMoreSlice() {
        // 54 h = 2 full days + 6 h → 3 slices (24h + 24h + 6h).
        val lastSync = now.minusSeconds(54 * 3600L)
        val slices = SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now)!!
        assertEquals(3, slices.size)
    }

    // --- Slice boundary correctness ---

    @Test
    fun firstSliceStartsAtLastSyncTime() {
        val lastSync = now.minusMillis(5 * sliceMs)
        val slices = SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now)!!
        assertEquals(lastSync, slices.first().first)
    }

    @Test
    fun lastSliceEndsExactlyAtNow() {
        // Applies to both even and uneven gaps.
        val lastSync = now.minusSeconds(54 * 3600L)  // uneven
        val slices = SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now)!!
        assertEquals(now, slices.last().second)
    }

    @Test
    fun slicesAreContiguous_noGapsOrOverlaps() {
        val lastSync = now.minusSeconds(70 * 3600L)  // 2d 22h → 3 slices
        val slices = SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now)!!
        for (i in 1 until slices.size) {
            assertEquals(
                "slice[$i].start must equal slice[${i - 1}].end",
                slices[i - 1].second,
                slices[i].first,
            )
        }
    }

    @Test
    fun fullPeriodCoverage_firstStartAndLastEndBoundReplayWindow() {
        val lastSync = now.minusMillis(5 * sliceMs)
        val slices = SyncManager.planCatchUpSlices(lastSync.toEpochMilli(), now)!!
        assertEquals(lastSync, slices.first().first)
        assertEquals(now, slices.last().second)
    }

    // --- Clamping to MAX_CATCHUP_DAYS ---

    @Test
    fun lastSyncOlderThanMaxCatchupDays_isClampedToEarliestAllowed() {
        val tooOld = now.minusSeconds((maxDays + 5) * 24 * 3600L)
        val slices = SyncManager.planCatchUpSlices(tooOld.toEpochMilli(), now)!!
        val expectedStart = now.minusSeconds(maxDays * 24 * 3600L)
        assertEquals(
            "first slice must start at now - MAX_CATCHUP_DAYS when lastSync is too old",
            expectedStart,
            slices.first().first,
        )
    }

    @Test
    fun clampedCatchUp_producesExactlyMaxCatchupDaysSlices() {
        // When clamped, the window is exactly maxDays × 24 h → maxDays slices.
        val tooOld = now.minusSeconds((maxDays + 10) * 24 * 3600L)
        val slices = SyncManager.planCatchUpSlices(tooOld.toEpochMilli(), now)!!
        assertEquals(maxDays.toInt(), slices.size)
    }
}
