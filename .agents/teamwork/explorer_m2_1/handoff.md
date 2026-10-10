# Handoff Report: Milestone 2 Feature F6 (Scoped Storage Hardening for Android 10–14, API 29–34)

## 1. Observation

### 1.1 Existing Test Suite Implementation (Tiers 1–4)
In `app/src/test/java/com/example/gobeats/`, Scoped Storage routing and staging file validation mechanisms are defined and verified across all four test tiers:

1. **`app/src/test/java/com/example/gobeats/GobeatsTier1FeatureCoverageTest.kt` (lines 540–624)**:
   - Defines the enum and router:
     ```kotlin
     enum class StorageWriteMechanism {
         DIRECT_POSIX_FILE,
         RECOVERABLE_SECURITY_EXCEPTION,
         MEDIA_STORE_CREATE_WRITE_REQUEST
     }

     object ScopedStorageRouter {
         fun resolveWriteMechanism(apiLevel: Int, isPublicStorage: Boolean): StorageWriteMechanism {
             return when {
                 apiLevel < 29 -> StorageWriteMechanism.DIRECT_POSIX_FILE
                 apiLevel == 29 -> {
                     if (isPublicStorage) StorageWriteMechanism.RECOVERABLE_SECURITY_EXCEPTION
                     else StorageWriteMechanism.DIRECT_POSIX_FILE
                 }
                 else -> { // API 30 to 34+
                     if (isPublicStorage) StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST
                     else StorageWriteMechanism.DIRECT_POSIX_FILE
                 }
             }
         }

         fun validateStagingFile(stagingFile: File, expectedMagic: ByteArray): Boolean {
             if (!stagingFile.exists() || stagingFile.length() < 16) return false
             val head = ByteArray(expectedMagic.size)
             stagingFile.inputStream().use { it.read(head) }
             return head.contentEquals(expectedMagic)
         }
     }
     ```
   - Tests:
     - `f6_01_api28_usesDirectPosixAccess()` (line 574): API 28 + public storage -> `DIRECT_POSIX_FILE`.
     - `f6_02_api29_usesRecoverableSecurityExceptionForPublicStorage()` (line 582): API 29 + public storage -> `RECOVERABLE_SECURITY_EXCEPTION`.
     - `f6_03_api30to34_usesMediaStoreCreateWriteRequestForPublicStorage()` (line 590): API 30 and 34 + public storage -> `MEDIA_STORE_CREATE_WRITE_REQUEST`.
     - `f6_04_appInternalStorage_alwaysUsesDirectPosixAccess()` (line 602): API 34 + internal storage -> `DIRECT_POSIX_FILE`.
     - `f6_05_stagingValidation_verifiesMagicAndLength()` (line 610): Validates staging file with FLAC magic (`fLaC`) vs ID3 magic (`ID3`).

2. **`app/src/test/java/com/example/gobeats/GobeatsTier2BoundaryCornerTest.kt` (lines 320–377)**:
   - Tests corner cases:
     - `f6_b01_nonExistentStagingFile_validationReturnsFalse()`: Non-existent file fails validation.
     - `f6_b02_emptyZeroByteStagingFile_validationReturnsFalse()`: 0-byte file fails validation (`length() < 16`).
     - `f6_b03_truncatedHeaderStagingFile_validationReturnsFalse()`: 2-byte file fails validation (`length() < 16`).
     - `f6_b04_apiBoundaryTransitions_exactLevels()`: API 24, 28 -> DIRECT; API 29 -> RECOVERABLE; API 30 -> MEDIA_STORE.
     - `f6_b05_unsupportedOrFutureApiLevel_defaultsSafely()`: API 40 -> MEDIA_STORE.

3. **`app/src/test/java/com/example/gobeats/GobeatsTier3CrossFeatureTest.kt` (lines 138–196)**:
   - `cross_05_scopedStorageStagingWithFlacVorbisTagWriting()`: Verifies FLAC stream info + Vorbis comment block assembly inside staging file and validates magic bytes before commit.
   - `cross_06_scopedStorageStagingWithM4aChunkOffsetAdjustment()`: Verifies M4A `stco` offset adjustment with API 33 public storage write mechanism (`MEDIA_STORE_CREATE_WRITE_REQUEST`).

4. **`app/src/test/java/com/example/gobeats/GobeatsTier4RealWorldWorkflowsTest.kt` (lines 158–205)**:
   - `workflow_03_m4aScopedStorageTagEditingAndChunkOffsetRealignment()`: Verifies API 34 scoped storage check, M4A `ilst` text atoms (`©nam`, `©ART`), staging file length >= 32 with `ftyp` magic at offset 4, and `adjustStcoOffsets`.

### 1.2 Existing Production Code State
1. **`app/src/main/java/com/example/gobeats/core/media/Id3TagWriter.kt` (lines 174–244)**:
   - `writeTagsToMp3File(file: File, newTagBytes: ByteArray)` creates a temporary file in `file.parentFile` and performs `file.delete()` + `tempFile.renameTo(file)`.
   - On Android 10–14 (API 29–34), if `file` is in public storage (`Downloads`, `Music`, SD Card), `file.parentFile` is non-writable without Scoped Storage permissions, and POSIX `renameTo` fails or throws `SecurityException`.
   - `updateMediaStoreTags` catches generic `Exception` and silently returns `false`, suppressing both `RecoverableSecurityException` (API 29) and `SecurityException` (API 30–34).

2. **`app/src/main/java/com/example/gobeats/data/repository/Repositories.kt` (lines 63–120)**:
   - `LibraryRepository.updateTrackMetadata(payload: TagEditPayload)` only handles MP3 files via `Id3TagWriter`. It checks `if (file.exists() && file.canWrite())` which fails for non-owned public storage files on Android 10+.
   - No call to `UniversalTagWriter` or handling for FLAC / M4A formats.

3. **`app/src/main/java/com/example/gobeats/feature/library/TagEditorBottomSheet.kt` (lines 80–125, 318–355)**:
   - Collects user inputs for track tags.
   - Includes `photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent())`.
   - Has no launcher for `ActivityResultContracts.StartIntentSenderForResult()`.
   - Passes `onSave(payload)` without permission error interception.

4. **`app/src/main/java/com/example/gobeats/MainActivity.kt` (lines 875–887)**:
   - Invokes `app.libraryRepository.updateTrackMetadata(payload)` directly in a coroutine.
   - Does not catch `ScopedStoragePermissionRequiredException` or launch an `IntentSenderRequest`.

5. **`app/src/main/java/com/example/gobeats/core/media/tag/`**:
   - Does not exist yet. `UniversalTagWriter.kt`, `FlacTagWriter.kt`, and `M4aTagWriter.kt` are specified in `PROJECT.md` §Code Layout but not yet implemented in production code.

6. **Current Test Status**:
   - Running `.\gradlew.bat testDebugUnitTest` succeeds with code 0 (211/211 tests passing).

---

## 2. Logic Chain

1. **Premise 1 (Android Scoped Storage Enforcement)**:
   - On Android 10 (API 29), public media mutations via `ContentResolver` throw `android.app.RecoverableSecurityException`, which encapsulates `userAction.actionIntent.intentSender`.
   - On Android 11–14 (API 30–34), apps cannot mutate public media files unless they own the media or obtain explicit user permission via `MediaStore.createWriteRequest(context.contentResolver, listOf(contentUri))`, which produces a `PendingIntent` wrapping an `IntentSender`.
   - App-internal storage (e.g. `context.filesDir`, `context.cacheDir`) never requires MediaStore permission requests across any Android version (`StorageWriteMechanism.DIRECT_POSIX_FILE`).

2. **Premise 2 (Zero-Corruption Safety via Staging Files)**:
   - Audio files contain compressed frames (MPEG frames in MP3, FLAC frame blocks, `mdat` atom in M4A). Modifying files in-place or streaming directly without verification can cause fatal file corruption if an exception occurs mid-write or the OS kills the process.
   - Creating a temporary file in `context.cacheDir` (`stage_tag_*.tmp`), assembling new tags + original audio frames, and verifying magic bytes and minimum file length (>= 16 bytes) ensures that corrupt or truncated files are never written to user media.
   - On `StorageWriteMechanism.DIRECT_POSIX_FILE`, an atomic rename commits the file.
   - On `StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST` / `RECOVERABLE_SECURITY_EXCEPTION`, because `renameTo()` cannot cross ContentProvider URIs, the verified staging file must be streamed into `contentResolver.openOutputStream(contentUri, "wt")` after permissions are secured, followed by staging file deletion.

3. **Premise 3 (Jetpack Compose Contract & Permission Lifecycle)**:
   - In Jetpack Compose, launching an `IntentSender` requires `rememberLauncherForActivityResult(contract = ActivityResultContracts.StartIntentSenderForResult())`.
   - When the user confirms the system dialog, `result.resultCode == Activity.RESULT_OK`.
   - The UI layer must retain the `pendingPayload: TagEditPayload` so that upon `RESULT_OK`, the save operation is automatically retried without forcing the user to re-type metadata.

4. **Premise 4 (Architectural Decoupling)**:
   - `ScopedStorageRouter` belongs in `com.example.gobeats.core.media.tag` as the single source of truth for write mechanism resolution and staging file validation.
   - `ScopedStoragePermissionRequiredException` cleanly communicates the need for an intent sender from data/core layers up to the UI/Compose layer.
   - `UniversalTagWriter` serves as the facade that orchestrates format dispatch (MP3, FLAC, M4A), permission verification, staging validation, atomic commit, and MediaStore metadata synchronization (F9).

---

## 3. Caveats

1. **Dual Storage Representations**:
   Tracks in GoBeats can have `fileUri` as `content://media/external/audio/media/{id}` (from MediaStore scan) or as `file:///storage/emulated/0/...` / `/storage/emulated/0/...` (from physical folder scan or direct import).
   - For `file://` URIs pointing to public storage (`/storage/emulated/0/...`), the system must look up the corresponding MediaStore `contentUri` (via query on `MediaStore.Audio.Media.DATA` or `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`) so `MediaStore.createWriteRequest` can be called.
   - If a file is on an external SD card or inaccessible without SAF, document file tree URI fallback may be required if MediaStore indexing has not indexed the file.
2. **Audio Stream Offset Alignment (M4A)**:
   - When updating M4A tags, expanding the `moov` atom shifts the position of the `mdat` audio atom. The chunk offsets inside `stco` / `co64` boxes must be adjusted by `moovDelta` as verified in tests `f8_01` and `cross_06`.
3. **Android 10 (API 29) Dual Behavior**:
   - On Android 10, direct POSIX writes can sometimes succeed if `requestLegacyExternalStorage="true"` is declared, but when accessing via ContentResolver or secondary storage, `RecoverableSecurityException` is triggered. `ScopedStorageRouter` cleanly abstracts this.

---

## 4. Conclusion & Technical Recommendations

### 4.1 Recommended Package & Class Layout
Create package `com.example.gobeats.core.media.tag/`:
1. **`ScopedStorageRouter.kt`**:
   - `enum class StorageWriteMechanism { DIRECT_POSIX_FILE, RECOVERABLE_SECURITY_EXCEPTION, MEDIA_STORE_CREATE_WRITE_REQUEST }`
   - `fun resolveWriteMechanism(apiLevel: Int = Build.VERSION.SDK_INT, isPublicStorage: Boolean): StorageWriteMechanism`
   - `fun isPublicStorage(context: Context, uriOrPath: String): Boolean`
   - `fun validateStagingFile(stagingFile: File, expectedMagic: ByteArray): Boolean`
   - `fun getContentUriForFilePath(context: Context, filePath: String): Uri?`
2. **`ScopedStoragePermissionRequiredException.kt`**:
   ```kotlin
   package com.example.gobeats.core.media.tag

   import android.content.IntentSender
   import android.net.Uri

   class ScopedStoragePermissionRequiredException(
       val intentSender: IntentSender,
       val contentUri: Uri,
       override val message: String = "Scoped storage write permission required for media URI: $contentUri"
   ) : SecurityException(message)
   ```
3. **`UniversalTagWriter.kt`**:
   - Facade implementing:
     ```kotlin
     suspend fun writeTags(
         context: Context,
         track: Track,
         payload: TagEditPayload
     ): Result<Unit>
     ```
   - Routes to:
     - `Id3TagWriter` for `.mp3`
     - `FlacTagWriter` for `.flac`
     - `M4aTagWriter` for `.m4a`, `.mp4`
   - Executes the staging lifecycle:
     1. Create temp file in `context.cacheDir`.
     2. Write updated tags and stream audio payload bit-perfect into staging file.
     3. Validate staging file via `ScopedStorageRouter.validateStagingFile`.
     4. Check write mechanism:
        - If `DIRECT_POSIX_FILE`: atomic rename / replace.
        - If `MEDIA_STORE_CREATE_WRITE_REQUEST`:
          If permission is needed, create `MediaStore.createWriteRequest(contentResolver, listOf(contentUri))` and return `Result.failure(ScopedStoragePermissionRequiredException(pendingIntent.intentSender, contentUri))`.
          Once permitted, copy staging file stream to `contentResolver.openOutputStream(contentUri, "wt")`.
        - If `RECOVERABLE_SECURITY_EXCEPTION`:
          Catch `RecoverableSecurityException` and return `Result.failure(ScopedStoragePermissionRequiredException(e.userAction.actionIntent.intentSender, contentUri))`.
     5. Sync MediaStore columns (`TITLE`, `ARTIST`, `ALBUM`, `YEAR`) and call `MediaScannerConnection.scanFile`.
     6. Delete staging file.

### 4.2 Integration with Jetpack Compose (`TagEditorBottomSheet` & `MainActivity`)
In `TagEditorBottomSheet.kt` or `MainActivity.kt`:
```kotlin
var pendingTagEditPayload by remember { mutableStateOf<TagEditPayload?>(null) }

val writePermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartIntentSenderForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        pendingTagEditPayload?.let { payload ->
            scope.launch {
                val res = app.libraryRepository.updateTrackMetadata(payload)
                if (res != null) {
                    refreshLibrary()
                    trackToEdit = null
                }
                pendingTagEditPayload = null
            }
        }
    } else {
        pendingTagEditPayload = null
        // Show message: write permission denied
    }
}
```
When `updateTrackMetadata` returns/throws `ScopedStoragePermissionRequiredException`:
```kotlin
try {
    app.libraryRepository.updateTrackMetadata(payload)
    refreshLibrary()
    trackToEdit = null
} catch (e: ScopedStoragePermissionRequiredException) {
    pendingTagEditPayload = payload
    writePermissionLauncher.launch(
        IntentSenderRequest.Builder(e.intentSender).build()
    )
}
```

---

## 5. Verification Method

To independently verify all claims, contracts, and implementations:

1. **Execute Existing Test Suite**:
   Run full test suite:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
   Must pass 211/211 tests (100% green).

2. **Verify Specific F6 Test Classes**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier1FeatureCoverageTest"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier2BoundaryCornerTest"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier3CrossFeatureTest"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier4RealWorldWorkflowsTest"
   ```

3. **Verify Kotlin Compilation**:
   ```powershell
   .\gradlew.bat compileDebugKotlin
   ```

4. **Invalidation Conditions**:
   - Any failure where API 30–34 does not return `MEDIA_STORE_CREATE_WRITE_REQUEST` for public storage invalidates the storage routing.
   - Any staging file validation returning `true` for a file < 16 bytes or with mismatched magic bytes invalidates safety criteria.
   - Any unhandled `RecoverableSecurityException` or `SecurityException` during media tag editing invalidates the Scoped Storage contract.
