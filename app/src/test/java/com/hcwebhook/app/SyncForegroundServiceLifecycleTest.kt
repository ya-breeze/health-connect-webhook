package com.hcwebhook.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncForegroundServiceLifecycleTest {

    @Test
    fun duplicateStartsCoalesceAndCompletionUsesNewestStartAndAllSchedules() = runBlocking {
        val rescheduled = mutableListOf<String>()
        val stopped = mutableListOf<Int>()
        val release = CompletableDeferred<Unit>()
        val jobs = mutableListOf<Job>()
        var launches = 0
        var cancellations = 0
        lateinit var coordinator: SyncForegroundServiceLifecycleCoordinator

        coordinator = coordinator(rescheduled, stopped)
        val first = coordinator.start(1, "morning") { generation ->
            launches++
            launch {
                try {
                    release.await()
                } catch (error: CancellationException) {
                    cancellations++
                    throw error
                } finally {
                    coordinator.complete(generation)
                }
            }.also { jobs += it }
        }
        val duplicate = coordinator.start(2, "evening") { error("duplicate must not launch") }

        assertTrue(first.shouldLaunch)
        assertFalse(duplicate.shouldLaunch)
        assertEquals(first.generation, duplicate.generation)
        assertEquals(1, launches)
        assertEquals(0, cancellations)
        assertTrue(jobs.single().isActive)
        assertTrue(rescheduled.isEmpty())
        assertTrue(stopped.isEmpty())

        release.complete(Unit)
        jobs.single().join()

        assertEquals(listOf("morning", "evening"), rescheduled)
        assertEquals(listOf(2), stopped)
    }

    @Test
    fun repeatedAndNullScheduleIdsAreRetainedWithoutInventingWork() {
        val rescheduled = mutableListOf<String>()
        val stopped = mutableListOf<Int>()
        val coordinator = coordinator(rescheduled, stopped)
        val job = Job()
        val first = coordinator.start(1, "daily") { job }

        coordinator.start(2, "daily") { error("duplicate must not launch") }
        coordinator.start(3, null) { error("duplicate must not launch") }
        coordinator.complete(first.generation)

        assertEquals(listOf("daily"), rescheduled)
        assertEquals(listOf(3), stopped)
        assertTrue(job.isActive)
        job.cancel()
    }

    @Test
    fun rescheduleFailureDoesNotLoseLaterSchedulesOrStopRequest() {
        val attempted = mutableListOf<String>()
        val failures = mutableListOf<Pair<String, Exception>>()
        val stopped = mutableListOf<Int>()
        val expectedFailure = IllegalStateException("alarm unavailable")
        val coordinator = SyncForegroundServiceLifecycleCoordinator(
            rescheduleAlarm = { scheduleId ->
                attempted += scheduleId
                if (scheduleId == "first") throw expectedFailure
            },
            reportRescheduleFailure = { scheduleId, error -> failures += scheduleId to error },
            requestStop = stopped::add,
        )
        val job = Job()
        val first = coordinator.start(1, "first") { job }
        coordinator.start(2, "second") { error("duplicate must not launch") }

        coordinator.complete(first.generation)

        assertEquals(listOf("first", "second"), attempted)
        assertEquals(listOf("first" to expectedFailure), failures)
        assertEquals(listOf(2), stopped)
        job.cancel()
    }

    @Test
    fun timeoutCancelsOnceAndLateCoroutineCleanupHasNoEffects() = runBlocking {
        val rescheduled = mutableListOf<String>()
        val stopped = mutableListOf<Int>()
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        var cancellations = 0
        var cleanupCount = 0
        val jobs = mutableListOf<Job>()
        lateinit var coordinator: SyncForegroundServiceLifecycleCoordinator
        coordinator = coordinator(rescheduled, stopped)

        coordinator.start(1, "first") { generation ->
            launch {
                try {
                    started.complete(Unit)
                    release.await()
                } catch (error: CancellationException) {
                    cancellations++
                    throw error
                } finally {
                    cleanupCount++
                    coordinator.complete(generation)
                }
            }.also { jobs += it }
        }
        coordinator.start(2, "second") { error("duplicate must not launch") }
        started.await()

        coordinator.timeout()

        assertEquals(listOf("first", "second"), rescheduled)
        assertEquals(listOf(2), stopped)

        jobs.single().join()

        assertEquals(listOf("first", "second"), rescheduled)
        assertEquals(listOf(2), stopped)
        assertEquals(1, cancellations)
        assertEquals(1, cleanupCount)
    }

    @Test
    fun staleCompletionCannotCancelOrStopAFollowingGeneration() {
        val rescheduled = mutableListOf<String>()
        val stopped = mutableListOf<Int>()
        val coordinator = coordinator(rescheduled, stopped)
        val first = coordinator.start(1, "first") { Job() }
        coordinator.complete(first.generation)

        val secondJob = Job()
        val second = coordinator.start(2, "second") { secondJob }
        coordinator.complete(first.generation)

        assertTrue(secondJob.isActive)
        assertEquals(listOf(1), stopped)
        assertEquals(listOf("first"), rescheduled)

        coordinator.complete(second.generation)
        assertEquals(listOf(1, 2), stopped)
        assertEquals(listOf("first", "second"), rescheduled)
        secondJob.cancel()
    }

    @Test
    fun destructionCancelsActiveWorkWithoutReschedulingOrStopping() = runBlocking {
        val rescheduled = mutableListOf<String>()
        val stopped = mutableListOf<Int>()
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        var cancellations = 0
        var cleanupCount = 0
        val jobs = mutableListOf<Job>()
        lateinit var coordinator: SyncForegroundServiceLifecycleCoordinator
        coordinator = coordinator(rescheduled, stopped)

        val job = coordinator.start(7, "scheduled") { generation ->
            launch {
                try {
                    started.complete(Unit)
                    release.await()
                } catch (error: CancellationException) {
                    cancellations++
                    throw error
                } finally {
                    cleanupCount++
                    coordinator.complete(generation)
                }
            }.also { jobs += it }
        }

        started.await()
        coordinator.destroy()
        jobs.single().join()

        assertEquals(1, cancellations)
        assertEquals(1, cleanupCount)
        assertTrue(job.shouldLaunch)
        assertTrue(rescheduled.isEmpty())
        assertTrue(stopped.isEmpty())
    }

    private fun coordinator(
        rescheduled: MutableList<String>,
        stopped: MutableList<Int>,
    ) = SyncForegroundServiceLifecycleCoordinator(
        rescheduleAlarm = rescheduled::add,
        reportRescheduleFailure = { _, error -> throw error },
        requestStop = stopped::add,
    )
}
