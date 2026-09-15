package com.hcwebhook.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A short-lived foreground service that performs a single background sync.
 *
 * This is used by [ScheduledSyncReceiver] instead of doing heavy I/O inside
 * BroadcastReceiver.goAsync(), which Android kills after ~10 s on API 34+.
 *
 * The service:
 *  1. Promotes itself to foreground with a transient notification.
 *  2. Calls [SyncManager.performSyncWithCatchUp].
 *  3. Reschedules retained alarms (for SCHEDULED mode).
 *  4. Requests a generation-safe stop — the notification disappears automatically.
 */
class SyncForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val syncManager: SyncManager by lazy { SyncManager(this) }
    private val lifecycleCoordinator = SyncForegroundServiceLifecycleCoordinator(
        rescheduleAlarm = ::rescheduleAlarmIfNeeded,
        requestStop = { newestStartId ->
            if (stopSelfResult(newestStartId)) {
                Log.d(TAG, "Stopped foreground sync for newest startId=$newestStartId")
            } else {
                Log.d(TAG, "Kept foreground sync alive for newer start than startId=$newestStartId")
            }
        },
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
        val notification = buildNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val scheduleId = intent?.getStringExtra(EXTRA_SCHEDULE_ID)
        val decision = lifecycleCoordinator.start(startId, scheduleId) { generation ->
            scope.launch {
                try {
                    val result = syncManager.performSyncWithCatchUp(syncType = "auto")
                    result.exceptionOrNull()?.let { error ->
                        Log.e(TAG, "Sync returned a failure in foreground service: ${error.message}", error)
                    }
                } catch (e: CancellationException) {
                    Log.d(TAG, "Foreground sync cancelled (generation=$generation)")
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Sync failed in foreground service: ${e.message}", e)
                } catch (e: OutOfMemoryError) {
                    // Error, not Exception — still must stop the FGS or Android 15 throws
                    // ForegroundServiceDidNotStopInTimeException after the dataSync quota.
                    Log.e(TAG, "Sync OOM in foreground service: ${e.message}", e)
                } finally {
                    lifecycleCoordinator.complete(generation)
                }
            }
        }

        if (decision.shouldLaunch) {
            Log.d(TAG, "Launching foreground sync (startId=$startId, scheduleId=$scheduleId, generation=${decision.generation})")
        } else {
            Log.d(
                TAG,
                "Coalescing duplicate foreground sync start (startId=$startId, scheduleId=$scheduleId, generation=${decision.generation})",
            )
        }

        return START_NOT_STICKY
    }

    /**
     * Android 15+ dataSync FGS: total 6 hours per 24 hours. Stop within a few
     * seconds when the system calls this, or the process crashes with
     * ForegroundServiceDidNotStopInTimeException.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "dataSync FGS timeout (callbackStartId=$startId, fgsType=$fgsType); cancelling sync")
        lifecycleCoordinator.timeout()
    }

    private fun rescheduleAlarmIfNeeded(scheduleId: String?) {
        if (scheduleId == null) return
        val prefsManager = PreferencesManager(this)
        val schedule = prefsManager.getScheduledSyncs().find { it.id == scheduleId }
        if (schedule != null && schedule.enabled) {
            ScheduledSyncManager(this).scheduleAlarm(schedule)
            Log.d(TAG, "Rescheduled alarm for next day: $scheduleId")
        }
    }

    override fun onDestroy() {
        lifecycleCoordinator.destroy()
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SyncForegroundService"
        private const val NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "hc_sync_service"
        const val EXTRA_SCHEDULE_ID = "schedule_id"

        fun start(context: Context, scheduleId: String? = null) {
            val intent = Intent(context, SyncForegroundService::class.java).apply {
                scheduleId?.let { putExtra(EXTRA_SCHEDULE_ID, it) }
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { e ->
                Log.e("SyncForegroundService", "Failed to start foreground service: ${e.message}", e)
            }
        }

        private fun ensureNotificationChannel(context: Context) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sync_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.sync_service_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        private fun buildNotification(context: Context): Notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.sync_service_notification_title))
                .setContentText(context.getString(R.string.sync_service_notification_text))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
    }
}

internal data class SyncForegroundServiceStartDecision(
    val generation: Long,
    val shouldLaunch: Boolean,
)

/**
 * Serializes the service lifecycle state without sharing it across service instances.
 *
 * One active run exists per service instance. Every start contributes its newest start ID
 * and optional schedule ID, while only completion or timeout may request a stop. A
 * generation token makes late coroutine cleanup harmless after timeout, destruction, or
 * a newer run has begun.
 */
internal class SyncForegroundServiceLifecycleCoordinator(
    private val rescheduleAlarm: (String) -> Unit,
    private val requestStop: (Int) -> Unit,
) {

    private val lock = Any()
    private var nextGeneration = 0L
    private var activeGeneration: Long? = null
    private var activeJob: Job? = null
    private var newestStartId: Int? = null
    private val retainedScheduleIds = LinkedHashSet<String>()
    private var finalizedGeneration = 0L
    private var destroyed = false

    fun start(
        startId: Int,
        scheduleId: String?,
        launch: (Long) -> Job,
    ): SyncForegroundServiceStartDecision = synchronized(lock) {
        if (destroyed) {
            return@synchronized SyncForegroundServiceStartDecision(
                generation = finalizedGeneration,
                shouldLaunch = false,
            )
        }

        // Record metadata before deciding whether this start launches or coalesces.
        newestStartId = startId
        scheduleId?.let(retainedScheduleIds::add)

        val currentGeneration = activeGeneration
        if (currentGeneration != null) {
            return@synchronized SyncForegroundServiceStartDecision(
                generation = currentGeneration,
                shouldLaunch = false,
            )
        }

        val generation = ++nextGeneration
        activeGeneration = generation
        activeJob = launch(generation)
        SyncForegroundServiceStartDecision(generation, shouldLaunch = true)
    }

    fun complete(generation: Long) {
        val finish = synchronized(lock) {
            if (activeGeneration != generation || finalizedGeneration >= generation) {
                return@synchronized null
            }
            detachLocked(generation)
        }
        finish?.let(::applyFinish)
    }

    fun timeout() {
        val finish = synchronized(lock) {
            val generation = activeGeneration ?: return@synchronized null
            val job = activeJob
            val result = detachLocked(generation)
            // Detach first so a cancellation-triggered finally block cannot finalize twice.
            job?.cancel()
            result
        }
        finish?.let(::applyFinish)
    }

    fun destroy() {
        synchronized(lock) {
            if (destroyed) return@synchronized
            destroyed = true
            activeGeneration = null
            activeJob?.cancel()
            activeJob = null
            newestStartId = null
            retainedScheduleIds.clear()
        }
    }

    private fun detachLocked(generation: Long): Finish {
        finalizedGeneration = generation
        activeGeneration = null
        activeJob = null
        val finish = Finish(
            scheduleIds = retainedScheduleIds.toList(),
            stopStartId = newestStartId,
        )
        retainedScheduleIds.clear()
        newestStartId = null
        return finish
    }

    private fun applyFinish(finish: Finish) {
        try {
            finish.scheduleIds.forEach(rescheduleAlarm)
        } finally {
            // A newer generation may have launched while the old generation's effects were
            // being applied. In that case its eventual completion owns the stop request.
            val mayRequestStop = synchronized(lock) {
                !destroyed && activeGeneration == null
            }
            if (mayRequestStop) {
                finish.stopStartId?.let(requestStop)
            }
        }
    }

    private data class Finish(
        val scheduleIds: List<String>,
        val stopStartId: Int?,
    )
}
