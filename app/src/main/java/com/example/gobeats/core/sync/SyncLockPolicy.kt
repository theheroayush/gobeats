package com.example.gobeats.core.sync

object SyncLockPolicy {
    const val WAKE_LOCK_TAG = "gobeats:sync_wakelock"
    const val WIFI_LOCK_TAG = "gobeats:wifi_sync_lock"
    const val DEFAULT_WAKE_LOCK_TIMEOUT_MS = 7_200_000L // 2 hours = 7,200,000 ms

    fun getWifiLockMode(apiLevel: Int): String {
        return if (apiLevel >= 29) {
            "WIFI_MODE_FULL_LOW_LATENCY"
        } else {
            "WIFI_MODE_FULL_HIGH_PERF"
        }
    }

    fun getWakeLockTimeoutMs(): Long = DEFAULT_WAKE_LOCK_TIMEOUT_MS
}
