package com.example.gobeats.core.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.gobeats.GobeatsApplication
import com.example.gobeats.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GobeatsSyncService : Service() {

    companion object {
        const val NOTIFICATION_ID = 42100
        const val CHANNEL_ID = "sync_channel"
        const val CHANNEL_NAME = "GoBeats PC Connect Sync"

        const val ACTION_START_SYNC = "com.example.gobeats.action.START_SYNC"
        const val ACTION_STOP_SYNC = "com.example.gobeats.action.STOP_SYNC"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, GobeatsSyncService::class.java).apply {
                action = ACTION_START_SYNC
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, GobeatsSyncService::class.java).apply {
                action = ACTION_STOP_SYNC
            }
            context.startService(intent)
        }
    }

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var progressJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SYNC -> {
                stopSync()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_SYNC, null -> {
                startSync()
            }
        }
        return START_STICKY
    }

    private fun startSync() {
        if (isServiceRunning) return
        isServiceRunning = true

        acquireLocks()

        val app = application as? GobeatsApplication
        val syncServer = app?.syncServer
        val serverUrl = syncServer?.let { "http://${it.getLocalIpAddress()}:${it.state.value.port}" } ?: "http://localhost:42100"

        val notification = buildNotification(
            title = "PC Connect Active",
            content = "Listening at $serverUrl (Doze Protection Active)"
        )

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        syncServer?.startServer()

        observeTransferProgress()
    }

    private fun observeTransferProgress() {
        progressJob?.cancel()
        val syncServer = (application as? GobeatsApplication)?.syncServer ?: return
        progressJob = scope.launch {
            syncServer.activeTransferStats.collectLatest { stats ->
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return@collectLatest
                if (stats != null) {
                    val percent = (stats.progressFraction * 100).toInt()
                    val speedStr = stats.formatSpeed()
                    val notif = buildNotification(
                        title = "Transferring: $percent% ($speedStr)",
                        content = "${stats.bytesTransferred / (1024 * 1024)}MB / ${stats.totalBytes / (1024 * 1024)}MB",
                        progress = percent,
                        maxProgress = 100
                    )
                    nm.notify(NOTIFICATION_ID, notif)
                } else {
                    val serverUrl = "http://${syncServer.getLocalIpAddress()}:${syncServer.state.value.port}"
                    val notif = buildNotification(
                        title = "PC Connect Active",
                        content = "Listening at $serverUrl (Doze Protection Active)"
                    )
                    nm.notify(NOTIFICATION_ID, notif)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        if (wifiLock?.isHeld != true) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lockMode = if (Build.VERSION.SDK_INT >= 29) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifiManager?.createWifiLock(lockMode, SyncLockPolicy.WIFI_LOCK_TAG)?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }

        if (wakeLock?.isHeld != true) {
            val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, SyncLockPolicy.WAKE_LOCK_TAG)?.apply {
                setReferenceCounted(false)
                acquire(SyncLockPolicy.getWakeLockTimeoutMs())
            }
        }
    }

    private fun releaseLocks() {
        runCatching {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        }
        wifiLock = null

        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun stopSync() {
        isServiceRunning = false
        progressJob?.cancel()
        progressJob = null

        val app = application as? GobeatsApplication
        app?.syncServer?.stopServer()

        releaseLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopSync()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification maintaining Wi-Fi transfer during Android Doze mode"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        progress: Int = -1,
        maxProgress: Int = -1
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GobeatsSyncService::class.java).apply {
            action = ACTION_STOP_SYNC
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sync", stopPendingIntent)

        if (progress >= 0 && maxProgress > 0) {
            builder.setProgress(maxProgress, progress, false)
        }

        return builder.build()
    }
}
