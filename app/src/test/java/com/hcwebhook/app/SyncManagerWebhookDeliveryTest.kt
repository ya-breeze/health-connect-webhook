package com.hcwebhook.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [SyncManager.deliverToWebhooks], the production coordinator
 * [SyncManager.performSync] delegates per-webhook delivery to. Injecting the
 * actual send lets these run as plain JVM tests without an Android [WebhookManager]
 * or [GrpcWebhookClient].
 */
class SyncManagerWebhookDeliveryTest {

    private val stepsOnly = MockPayloadBuilder.buildHealthData(enabledTypes = setOf("STEPS"))
    private val heartRateOnly = MockPayloadBuilder.buildHealthData(enabledTypes = setOf("HEART_RATE"))
    private val stepsAndHeartRate = MockPayloadBuilder.buildHealthData(enabledTypes = setOf("STEPS", "HEART_RATE"))

    private val jsonConfig = WebhookConfig(url = "https://json.example", deliveryFormat = WebhookDeliveryFormat.JSON)
    private val grpcConfig = WebhookConfig(url = "https://grpc.example", deliveryFormat = WebhookDeliveryFormat.GRPC)

    @Test
    fun allSuccessfulDeliveriesProduceASucceededOutcomeWithNotifications() = runBlocking {
        val delivered = mutableListOf<WebhookConfig>()
        val sharedNotifConfig = NotificationConfig()

        val outcome = SyncManager.deliverToWebhooks(
            healthData = stepsAndHeartRate,
            enabledWebhookConfigs = listOf(jsonConfig, grpcConfig),
            requireAllWebhookDeliveries = true,
            notificationConfigsFor = { listOf(sharedNotifConfig) },
            deliver = { config, _, _ ->
                delivered.add(config)
                WebhookDeliveryAttempt.Delivered(Result.success(Unit))
            },
        )

        assertEquals(listOf(jsonConfig, grpcConfig), delivered)
        assertTrue(outcome.attempted)
        assertTrue(outcome.succeeded)
        assertNull(outcome.failure)
        assertEquals(1, outcome.notifications.size)
        assertEquals(2, outcome.notifications.values.single().size)
        assertTrue(outcome.notifications.values.single().all { it.startsWith("✅") })
    }

    @Test
    fun allFailedDeliveriesProduceAFailedOutcomeRegardlessOfRequireAll() = runBlocking {
        val failure = IllegalStateException("connection refused")

        val outcome = SyncManager.deliverToWebhooks(
            healthData = stepsOnly,
            enabledWebhookConfigs = listOf(jsonConfig, grpcConfig),
            requireAllWebhookDeliveries = false,
            notificationConfigsFor = { emptyList() },
            deliver = { _, _, _ -> WebhookDeliveryAttempt.Delivered(Result.failure(failure)) },
        )

        assertTrue(outcome.attempted)
        assertFalse(outcome.succeeded)
        assertEquals(failure, outcome.failure)
    }

    @Test
    fun mixedBatchWithRequireAllReturnsFailureEvenThoughOneDestinationSucceeded() = runBlocking {
        val failure = IllegalStateException("second webhook rejected")

        val outcome = SyncManager.deliverToWebhooks(
            healthData = stepsAndHeartRate,
            enabledWebhookConfigs = listOf(jsonConfig, grpcConfig),
            requireAllWebhookDeliveries = true,
            notificationConfigsFor = { emptyList() },
            deliver = { config, _, _ ->
                if (config == jsonConfig) WebhookDeliveryAttempt.Delivered(Result.success(Unit))
                else WebhookDeliveryAttempt.Delivered(Result.failure(failure))
            },
        )

        assertTrue(outcome.attempted)
        assertFalse(outcome.succeeded)
        assertEquals(failure, outcome.failure)
    }

    @Test
    fun mixedBatchWithoutRequireAllSucceedsOnAnySuccess() = runBlocking {
        val outcome = SyncManager.deliverToWebhooks(
            healthData = stepsAndHeartRate,
            enabledWebhookConfigs = listOf(jsonConfig, grpcConfig),
            requireAllWebhookDeliveries = false,
            notificationConfigsFor = { emptyList() },
            deliver = { config, _, _ ->
                if (config == jsonConfig) WebhookDeliveryAttempt.Delivered(Result.success(Unit))
                else WebhookDeliveryAttempt.Delivered(Result.failure(IllegalStateException("rejected")))
            },
        )

        assertTrue(outcome.attempted)
        assertTrue(outcome.succeeded)
    }

    @Test
    fun noConfiguredWebhookHasMatchingDataAfterFiltering() = runBlocking {
        var deliverCalls = 0

        val outcome = SyncManager.deliverToWebhooks(
            healthData = heartRateOnly,
            enabledWebhookConfigs = listOf(jsonConfig.copy(dataTypeFilter = setOf("STEPS"))),
            requireAllWebhookDeliveries = true,
            notificationConfigsFor = { emptyList() },
            deliver = { _, _, _ -> deliverCalls++; WebhookDeliveryAttempt.Delivered(Result.success(Unit)) },
        )

        assertEquals(0, deliverCalls)
        assertFalse(outcome.attempted)
        assertTrue(outcome.notifications.isEmpty())
    }

    @Test
    fun eachWebhookOnlySeesItsOwnDataTypeFilter() = runBlocking {
        val filteredConfig = jsonConfig.copy(dataTypeFilter = setOf("STEPS"))
        val unfilteredConfig = grpcConfig
        val seenRecordCounts = mutableMapOf<WebhookConfig, Int>()

        val outcome = SyncManager.deliverToWebhooks(
            healthData = stepsAndHeartRate,
            enabledWebhookConfigs = listOf(filteredConfig, unfilteredConfig),
            requireAllWebhookDeliveries = true,
            notificationConfigsFor = { emptyList() },
            deliver = { config, filteredData, totalRecords ->
                seenRecordCounts[config] = totalRecords
                if (config == filteredConfig) {
                    assertTrue(filteredData.heartRate.isEmpty())
                    assertFalse(filteredData.steps.isEmpty())
                } else {
                    assertFalse(filteredData.heartRate.isEmpty())
                    assertFalse(filteredData.steps.isEmpty())
                }
                WebhookDeliveryAttempt.Delivered(Result.success(Unit))
            },
        )

        assertTrue(outcome.succeeded)
        assertEquals(stepsAndHeartRate.steps.size, seenRecordCounts.getValue(filteredConfig))
        assertEquals(
            stepsAndHeartRate.steps.size + stepsAndHeartRate.heartRate.size,
            seenRecordCounts.getValue(unfilteredConfig),
        )
    }

    @Test
    fun payloadBuildFailureCountsAsFailureWithoutANotification() = runBlocking {
        val oom = OutOfMemoryError("too many records")

        val outcome = SyncManager.deliverToWebhooks(
            healthData = stepsOnly,
            enabledWebhookConfigs = listOf(jsonConfig),
            requireAllWebhookDeliveries = true,
            notificationConfigsFor = { listOf(NotificationConfig()) },
            deliver = { _, _, _ -> WebhookDeliveryAttempt.PayloadBuildFailed(Exception("oom", oom)) },
        )

        assertTrue(outcome.attempted)
        assertFalse(outcome.succeeded)
        assertEquals("oom", outcome.failure?.message)
        assertTrue(outcome.notifications.isEmpty())
    }

    @Test
    fun mixedWebhookBatchThroughRunAutomaticSyncAdvancesNeitherAutomaticNorGeneralProgress() = runBlocking {
        val events = mutableListOf<String>()
        val existing = java.time.Instant.parse("2026-06-01T09:00:00Z").toEpochMilli()
        val now = java.time.Instant.parse("2026-06-01T12:00:00Z")
        val failure = IllegalStateException("second destination failed")

        val result = SyncManager.runAutomaticSync(
            automaticSyncMs = existing,
            generalSyncMs = null,
            now = now,
            syncType = "auto",
            persistAutomaticSyncMs = { events.add("automatic:$it") },
            persistGeneralSyncMs = { events.add("general:$it") },
            completionTimeMs = { error("failed run does not need completion time") },
            sync = { _, _, _, _ ->
                // Mirrors performSync: route one success and one failure through
                // the real coordinator, then translate its outcome to a Result
                // the same way performSync does.
                val outcome = SyncManager.deliverToWebhooks(
                    healthData = stepsAndHeartRate,
                    enabledWebhookConfigs = listOf(jsonConfig, grpcConfig),
                    requireAllWebhookDeliveries = true,
                    notificationConfigsFor = { emptyList() },
                    deliver = { config, _, _ ->
                        if (config == jsonConfig) WebhookDeliveryAttempt.Delivered(Result.success(Unit))
                        else WebhookDeliveryAttempt.Delivered(Result.failure(failure))
                    },
                )
                events.add("sync")
                if (outcome.succeeded) Result.success(SyncResult.Success(emptyMap()))
                else Result.failure(outcome.failure ?: Exception("Failed to post to webhooks"))
            },
            pauseBetweenSlices = { error("normal sync must not pause") },
        )

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
        assertEquals(listOf("sync"), events)
    }
}
