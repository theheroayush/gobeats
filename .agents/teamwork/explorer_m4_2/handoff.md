# Milestone 4 Feature F14: Network Transfer Hardening — Exploration & Architecture Report

**Author:** explorer_m4_2  
**Target Feature:** Feature F14: Network Transfer Hardening (`GobeatsSyncService` foreground service with `FOREGROUND_SERVICE_DATA_SYNC`, ongoing notification, `WifiLock`, and `WakeLock` for Wi-Fi PC Connect transfers)  
**Date:** 2026-10-07  
**Status:** COMPLETE (Investigation Finished, Ready for Worker Implementation)  

---

## 1. Observation

### 1.1 Source Code & Manifest Gaps
1. **Missing Service Implementation**:
   - Investigated directory `app/src/main/java/com/example/gobeats/core/sync/`.
   - File listing contains only `GobeatsSyncServer.kt` (405 lines).
   - `GobeatsSyncService.kt` does **not exist anywhere** in the repository (`find_by_name` returned 0 matches).
2. **Missing Manifest Permissions & Declarations**:
   - Inspected `app/src/main/AndroidManifest.xml`:
     - Permissions section (lines 4–15):
       ```xml
       <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
       <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
       <uses-permission android:name="android.permission.WAKE_LOCK" />
       ```
     - `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />` is **missing**.
     - Service declarations (lines 183–191): Only `.core.media.GobeatsMediaSessionService` is declared.
     - `<service android:name=".core.sync.GobeatsSyncService" ... />` is **missing**.
3. **Unprotected Wi-Fi Server Lifecycle**:
   - `GobeatsSyncServer.kt`:
     - Line 48: `class GobeatsSyncServer(private val context: Context, private val dbHelper: GobeatsDatabaseHelper)`
     - Lines 81–108: `startServer(port: Int = 42100)` opens a raw `ServerSocket` on an in-memory coroutine scope (`Dispatchers.IO`).
     - Lines 176–225: `handleFileUpload` streams incoming bytes in 8KB chunks via `FileOutputStream`, but does not calculate live transfer progress or throughput metrics (`TransferStats`).
   - `DevicesScreen.kt`:
     - Lines 156–159 and lines 240–243: Clicking "Connect Device" or "Connect Your Computer" calls `syncServer?.startServer()` directly inside the UI process without starting a foreground service or acquiring any locks.
     - When the device screen turns off or the app transitions to the background, Android Doze mode suspends the CPU and throttles the Wi-Fi interface, causing socket dropouts during large file transfers.
4. **Toolchain & Environment Constraints**:
   - `app/build.gradle.kts` specifies `compileSdk = 36`, `targetSdk = 36`, and `jvmToolchain(17)`.
   - On the Windows 11 ARM64 host, Foojay automatic toolchain resolution fails. JDK 17 is locally installed at `C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`. Executing `./gradlew.bat` requires `$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"`.

---

### 1.2 Test Suite Contracts & Assertions

#### Tier 1 Feature Coverage (`GobeatsTier1FeatureCoverageTest.kt`)
- **`TransferStats` Data Model** (lines 1316–1323):
  ```kotlin
  data class TransferStats(
      val bytesTransferred: Long,
      val totalBytes: Long,
      val durationSeconds: Double
  ) {
      val progressFraction: Float = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
      val speedMbPerSec: Double = if (durationSeconds > 0) (bytesTransferred / (1024.0 * 1024.0)) / durationSeconds else 0.0
  }
  ```
- **`SyncLockPolicy` Policy Model** (lines 1325–1335):
  ```kotlin
  object SyncLockPolicy {
      fun getWifiLockMode(apiLevel: Int): String {
          return if (apiLevel >= 29) {
              "WIFI_MODE_FULL_LOW_LATENCY"
          } else {
              "WIFI_MODE_FULL_HIGH_PERF"
          }
      }

      fun getWakeLockTimeoutMs(): Long = 2 * 60 * 60 * 1000L // 2 hours = 7_200_000L
  }
  ```
- **Tests**:
  - `f14_01_wifiLockModeSelection_byApiLevel`: Verifies API 34 & 29 -> `"WIFI_MODE_FULL_LOW_LATENCY"`; API 28 -> `"WIFI_MODE_FULL_HIGH_PERF"`.
  - `f14_02_wakeLockTimeout_isBoundedToPreventBatteryDrain`: Verifies WakeLock timeout equals `7_200_000L` (2 hours).
  - `f14_03_transferStats_computesProgressAndSpeedAccurately`: Verifies 50MB of 100MB in 5.0s yields `0.5f` progress and `10.0 MB/s`.
  - `f14_04_zeroByteTransfer_handlesDivisionSafely`: Verifies `bytesTransferred = 0L, totalBytes = 0L, durationSeconds = 0.0` yields `progressFraction = 0.0f` and `speedMbPerSec = 0.0`.
  - `f14_05_lockLifecycle_mustReleaseOnCompletionOrError`: Verifies that locks acquire on start and are strictly released on completion or failure.

#### Tier 2 Boundary & Corner Cases (`GobeatsTier2BoundaryCornerTest.kt`)
- `f14_b01_zeroDurationTransfer_speedDefaultsSafely`: `durationSeconds = 0.0` -> `speedMbPerSec = 0.0`, `progressFraction = 1.0f`.
- `f14_b02_largeFileTransfer_exceeding32BitIntegers`: 50GB file (`50L * 1024 * 1024 * 1024`) does not overflow 32-bit integers; speed > 90 MB/s computed cleanly.
- `f14_b03_progressCannotExceedUnity`: `bytesTransferred = 200L, totalBytes = 100L` clamped to `1.0f`.
- `f14_b04_apiLevelBoundaryForWifiLock`: Boundaries at API 24, 28 -> HIGH_PERF; 29 -> LOW_LATENCY.
- `f14_b05_abortedTransfer_locksReleasedSafely`: Aborted transfers guarantee immediate lock release.

#### Tier 4 Real-World Workflows (`GobeatsTier4RealWorldWorkflowsTest.kt`)
- `workflow_05_wifiPcConnectTransferAndDozeModeHardeningLifecycle` (lines 267–320):
  - User starts sync server from DevicesScreen.
  - Verifies API 34 policy: `wifiLockMode == "WIFI_MODE_FULL_LOW_LATENCY"`, `wakeLockTimeoutMs == 7200000L`.
  - Verifies simultaneous acquisition: `isWifiLockAcquired`, `isWakeLockAcquired`, `isForegroundServiceActive`.
  - Verifies intermediate progress: 75MB of 150MB in 7.5s -> `0.5f` progress, `10.0 MB/s`.
  - Verifies completion: 150MB of 150MB in 15.0s -> `1.0f` progress.
  - Verifies clean shutdown: on stop or error, all locks released and foreground service terminated.

---

## 2. Logic Chain

1. **Android 14+ Foreground Service Enforcement**:
   - `targetSdk` is 36. Starting in Android 14 (API 34), any app invoking `startForeground(...)` without declaring the corresponding foreground service type in `AndroidManifest.xml` and passing it at runtime triggers a `MissingForegroundServiceTypeException`.
   - Therefore, `FOREGROUND_SERVICE_DATA_SYNC` must be declared in both `<uses-permission>` and `<service android:foregroundServiceType="dataSync">`.
   - At runtime, `startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)` must be passed on API 29+.

2. **Doze Mode & Socket Termination Prevention**:
   - Android aggressive power management (Doze Mode and App Standby) disables Wi-Fi packet processing and suspends user-space threads when the screen is off unless:
     a) A Foreground Service with an ongoing notification is active.
     b) A `WifiManager.WifiLock` is held.
     c) A `PowerManager.WakeLock` (`PARTIAL_WAKE_LOCK`) is held.
   - For high-bandwidth music and video streaming, `WifiManager.WIFI_MODE_FULL_LOW_LATENCY` (API 29+) disables Wi-Fi packet batching and power-save poll delays, maximizing throughput. On older devices (< API 29), `WifiManager.WIFI_MODE_FULL_HIGH_PERF` must be used.

3. **Battery Drain Safeguards**:
   - Indefinitely held WakeLocks deplete battery if a user starts Wi-Fi sync and forgets about it.
   - Per `SyncLockPolicy.getWakeLockTimeoutMs()`, the WakeLock acquisition must be bounded to `7_200_000L` (2 hours).
   - In all exit scenarios (normal stop, client error, exception during stream read, `onDestroy`), `releaseLocks()` must be called safely with `isHeld` verification and `runCatching` protection.

4. **Ongoing Notification & UX**:
   - The notification must be assigned to a `NotificationChannel` with `IMPORTANCE_LOW` (so it does not ping repeatedly during updates).
   - It must be marked ongoing (`setOngoing(true)`), show the local IP/port, show real-time transfer progress when files upload, and provide a direct `"Stop"` action PendingIntent so the user can terminate sync from the status bar.

5. **Decoupled Architecture for Empirical Testability**:
   - In line with Milestones 1–3, business logic and mathematical algorithms (`SyncLockPolicy`, `TransferStats`, transfer calculation) should be encapsulated in clean, pure Kotlin classes under `com.example.gobeats.core.sync`. This enables JVM unit tests to verify 100% of the mathematical, edge-case, and boundary logic without depending on Android framework mocks.

---

## 3. Caveats

1. **Host Execution Environment**:
   - PowerShell terminal commands calling Gradle must prefix `$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"` because the default system `JAVA_HOME` points to JDK 21, which triggers Foojay auto-download failures on Windows ARM64. Alternatively, adding `org.gradle.java.installations.paths=C:\\Program Files\\Microsoft\\jdk-17.0.19.10-hotspot` to `gradle.properties` solves this permanently.
2. **Notification Permission on Android 13+**:
   - `POST_NOTIFICATIONS` is already present in `AndroidManifest.xml` (line 8). For Android foreground services, notifications are shown in the notification drawer; runtime permission is needed for alert popups, but foreground service status remains active regardless.
3. **No Existing Source Conflicts**:
   - Since `GobeatsSyncService.kt` does not exist yet, creating it will not break existing code. Existing test files (`GobeatsTier1FeatureCoverageTest.kt`) define internal test harnesses that do not clash with new production classes in `com.example.gobeats.core.sync`.

---

## 4. Conclusion & Actionable Implementation Plan for Worker

### File 1: `app/src/main/AndroidManifest.xml`
- **Action**: Add permission and service declaration.
- **Diff snippet**:
  ```xml
  <!-- In permissions block -->
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
  
  <!-- Inside <application> block -->
  <service
      android:name=".core.sync.GobeatsSyncService"
      android:exported="false"
      android:foregroundServiceType="dataSync" />
  ```

---

### File 2: `app/src/main/java/com/example/gobeats/core/sync/SyncLockPolicy.kt` (NEW FILE)
- **Action**: Create pure Kotlin models for lock policy and transfer metrics.
- **Content**:
  ```kotlin
  package com.example.gobeats.core.sync

  import java.util.Locale

  data class TransferStats(
      val bytesTransferred: Long,
      val totalBytes: Long,
      val durationSeconds: Double
  ) {
      val progressFraction: Float = if (totalBytes > 0) {
          (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f)
      } else {
          0f
      }

      val speedMbPerSec: Double = if (durationSeconds > 0) {
          (bytesTransferred / (1024.0 * 1024.0)) / durationSeconds
      } else {
          0.0
      }

      fun formatSpeed(): String = String.format(Locale.US, "%.1f MB/s", speedMbPerSec)
  }

  object SyncLockPolicy {
      const val WAKE_LOCK_TAG = "gobeats:sync_wake_lock"
      const val WIFI_LOCK_TAG = "gobeats:wifi_sync_lock"
      const val DEFAULT_WAKE_LOCK_TIMEOUT_MS = 7_200_000L // 2 hours

      fun getWifiLockMode(apiLevel: Int): String {
          return if (apiLevel >= 29) {
              "WIFI_MODE_FULL_LOW_LATENCY"
          } else {
              "WIFI_MODE_FULL_HIGH_PERF"
          }
      }

      fun getWakeLockTimeoutMs(): Long = DEFAULT_WAKE_LOCK_TIMEOUT_MS
  }
  ```

---

### File 3: `app/src/main/java/com/example/gobeats/core/sync/GobeatsSyncService.kt` (NEW FILE)
- **Action**: Implement the complete Android Foreground Service.
- **Key Responsibilities**:
  1. **Lifecycle**: Inherits `android.app.Service()`.
  2. **Notification Channel**: Creates `"gobeats_pc_sync_channel"`, Name `"GoBeats PC Connect Sync"`, Importance `IMPORTANCE_LOW`.
  3. **Ongoing Notification**:
     - Content: Server URL or live transfer progress (`"Transferring <filename> (50%)"`).
     - Action: `"Stop Sync"` PendingIntent with `ACTION_STOP_SYNC`.
     - ContentIntent: PendingIntent launching `MainActivity`.
  4. **Foreground Launch**:
     - Calls `startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)` on Android 29+.
  5. **Locks Management**:
     - `WifiManager.WifiLock`: `WIFI_MODE_FULL_LOW_LATENCY` on API 29+, `WIFI_MODE_FULL_HIGH_PERF` on older APIs. Tag: `"gobeats:wifi_sync_lock"`.
     - `PowerManager.WakeLock`: `PARTIAL_WAKE_LOCK`. Tag: `"gobeats:sync_wake_lock"`. Timeout: `SyncLockPolicy.getWakeLockTimeoutMs()`.
     - Safe release in `stopSync()`, `onDestroy()`, and on error: checks `isHeld` before release, wraps in `runCatching`.
  6. **Server Integration**:
     - Launches `(application as GobeatsApplication).syncServer.startServer()` on `ACTION_START_SYNC`.
     - Calls `syncServer.stopServer()` on `ACTION_STOP_SYNC`.
     - Observes transfer progress to dynamically update notification progress bar and speed.

---

### File 4: `app/src/main/java/com/example/gobeats/core/sync/GobeatsSyncServer.kt`
- **Action**: Add live transfer progress callbacks / flow to `handleFileUpload`.
- **Modifications**:
  - Add `var onTransferProgress: ((fileName: String, stats: TransferStats) -> Unit)? = null`.
  - In `handleFileUpload(input, output, contentLength, contentType, originalFileName)`:
    - Track start time (`System.currentTimeMillis()`).
    - Inside buffer read loop, periodically (e.g. every 256KB or 200ms) compute `TransferStats(bytesTransferred, contentLength.toLong(), durationSeconds)` and invoke `onTransferProgress`.
  - In `GobeatsSyncService`, subscribe to this callback to call `updateNotification(fileName, stats)`.

---

### File 5: `app/src/main/java/com/example/gobeats/feature/devices/DevicesScreen.kt`
- **Action**: Wire UI actions to start/stop `GobeatsSyncService`.
- **Modifications**:
  - In "Connect Device" button (lines 156–159) and "Connect Your Computer" banner (lines 240–243):
    - Replace raw `syncServer?.startServer()` with `GobeatsSyncService.start(context)`.
  - In `AlertDialog` (connect modal, lines 657–765):
    - Add a "Stop Server" button that invokes `GobeatsSyncService.stop(context)` and dismisses the dialog.

---

### File 6: `app/src/test/java/com/example/gobeats/Milestone4NetworkHardeningStressTest.kt` (NEW TEST FILE)
- **Action**: Create an exhaustive empirical stress test suite mirroring M2 and M3 stress test suites.
- **Coverage**:
  - `TransferStats` calculation under multi-gigabyte files (up to 100 GB).
  - Division by zero safety for 0 duration and 0 bytes.
  - Progress fraction clamping to `1.0f`.
  - Throughput calculations (e.g. 50 MB/s, 100 MB/s).
  - API level lock mode resolution across API 21, 28, 29, 33, 34, 36.
  - Bounded WakeLock duration validation.
  - Simulated lock acquisition and safe release under simulated network aborts and socket dropouts.

---

## 5. Verification Method

1. **Compile & Type-Check**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
   .\gradlew.bat compileDebugKotlin
   ```
   *Expected*: `BUILD SUCCESSFUL` with 0 errors.

2. **Run F14 Isolated Unit Tests**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier1FeatureCoverageTest.f14*"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier2BoundaryCornerTest.f14*"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier4RealWorldWorkflowsTest.workflow_05*"
   ```
   *Expected*: All 11 tests pass with 0 failures.

3. **Run New M4 Stress Test Suite**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.Milestone4NetworkHardeningStressTest"
   ```
   *Expected*: 100% passing.

4. **Run Full Project Test Suite**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
   .\gradlew.bat testDebugUnitTest
   ```
   *Expected*: All unit tests across all test suites pass with 0 failures.

5. **Assemble Final Debug APK**:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
   .\gradlew.bat assembleDebug
   ```
   *Expected*: `BUILD SUCCESSFUL`, `app-debug.apk` produced cleanly.
