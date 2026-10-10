# Handoff Report: Feature F12 — Offline Synced Lyrics Fallback

**Agent**: `explorer_m3_3`  
**Role**: Codebase Explorer & Synthesizer (F12 Focus)  
**Date**: 2026-10-06  
**Status**: COMPLETE (Hard Handoff)  
**Target Recipient**: Worker Agent (`worker_m3_3` or designated M3 implementer) and Orchestrator (`parent`)

---

## 1. Observation

Direct observations from examining the GoBeats codebase and test suites:

### 1.1 Existing Lyrics Pipeline in `LyricsRepository.kt`
- Location: `app/src/main/java/com/example/gobeats/core/lyrics/LyricsRepository.kt`
- **Companion File Discovery (lines 28–46)**:
  ```kotlin
  val uri = Uri.parse(track.fileUri)
  if (uri.scheme == "file" || track.fileUri.startsWith("/")) {
      val audioPath = uri.path ?: track.fileUri
      val audioFile = File(audioPath)
      val baseName = audioFile.nameWithoutExtension
      val companionLrc = File(audioFile.parentFile, "$baseName.lrc")
      val companionTxt = File(audioFile.parentFile, "$baseName.txt")
  ```
  **Direct Flaw**: `uri.scheme == "file" || track.fileUri.startsWith("/")` strictly excludes MediaStore content URIs (`content://media/external/audio/media/...`). On standard Android setups, `MediaScanner.kt` populates `track.fileUri` as `content://...` (via `ContentUris.withAppendedId(collection, id)`), meaning companion `.lrc` discovery is currently completely bypassed for scanned local library tracks. Furthermore, the search is exact-case only (`$baseName.lrc`), missing uppercase extensions like `$baseName.LRC`.

- **Embedded Lyrics Handling (lines 48–65)**:
  ```kotlin
  val retriever = MediaMetadataRetriever()
  try {
      val uri = Uri.parse(track.fileUri)
      retriever.setDataSource(context, uri)
      val comment = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION)
      if (!comment.isNullOrBlank() && (comment.contains("[00:") || comment.contains("\n"))) {
          val parsed = LrcParser.parse(comment, source = "embedded")
  ```
  **Direct Flaw**: Android's `MediaMetadataRetriever` does not have a native lyric key. Querying `METADATA_KEY_COMPILATION` does not decode ID3 `USLT` (unsynchronized lyrics) or `SYLT` (synchronized lyrics), Vorbis comment `LYRICS` in FLAC, or `©lyr` atoms in M4A.

- **Missing Interface Contract from `PROJECT.md` §M3 (line 63)**:
  `PROJECT.md` specifies:
  ```kotlin
  - LyricsRepository:
    - getOfflineLyrics(track: Track): SyncedLyrics? (Checks embedded ID3 USLT/SYLT, then companion <name>.lrc).
  ```
  `LyricsRepository.kt` currently only exposes `getLyricsForTrack(track: Track): LyricsData`. It does NOT expose `getOfflineLyrics(track: Track)`.
  Additionally, `typealias SyncedLyrics = LyricsData` is not yet declared.

### 1.2 Tag Writing vs Tag Reading Capabilities
- `Id3TagWriter.kt` (`com.example.gobeats.core.media.Id3TagWriter.kt`):
  - Contains encoding helpers: `buildId3v23Tag`, `buildTextFrame`, `encodeSynchsafeInteger`, `decodeSynchsafeInteger`.
  - Does **NOT** contain an ID3 tag reader, frame parser, or `USLT`/`SYLT` decoder.
- `FlacTagWriter.kt` (`com.example.gobeats.core.media.FlacTagWriter.kt`):
  - Contains `readMetadataBlocks(input: InputStream): List<RawMetadataBlock>` (lines 199–230).
  - Contains `parseVorbisComments(blockBytes: ByteArray): Map<String, String>` (lines 100–123).
  - Ready to be leveraged for reading `LYRICS` / `UNSYNCEDLYRICS` / `SYNCEDLYRICS` from FLAC metadata blocks.
- `M4aTagWriter.kt` (`com.example.gobeats.core.media.M4aTagWriter.kt`):
  - Traverses MP4 atoms (`moov`, `udta`, `meta`, `ilst`).
  - Does not currently expose a direct reader for the `©lyr` atom.

### 1.3 Reference Implementations in Other Modules
- Path Resolution for Content URIs in `SubtitleDetector.kt` (`com.example.gobeats.core.video.SubtitleDetector.kt:38-44, 149-162`):
  ```kotlin
  val filePath = when {
      parsedUri.scheme == ContentResolver.SCHEME_FILE -> parsedUri.path
      parsedUri.scheme == null || parsedUri.scheme == "" -> track.fileUri
      parsedUri.scheme == ContentResolver.SCHEME_CONTENT -> resolveFilePathFromContentUri(context, parsedUri)
      else -> null
  }
  ...
  private fun resolveFilePathFromContentUri(context: Context, uri: Uri): String? {
      try {
          val projection = arrayOf(MediaStore.MediaColumns.DATA)
          context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
              if (cursor.moveToFirst()) {
                  val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                  if (idx != -1) return cursor.getString(idx)
              }
          }
      } catch (_: Exception) {}
      return null
  }
  ```
  This is the exact pattern required in `LyricsRepository` to auto-discover companion `.lrc` files from MediaStore content URIs.

### 1.4 Test Suite Assertions for F12
- `GobeatsTier1FeatureCoverageTest.kt:1161–1260`:
  - `f12_01_companionLrcHasPriorityOverEmbeddedAndOnline`: Asserts `companion` wins over `embedded_id3` and `lrclib`.
  - `f12_02_embeddedId3UsedWhenCompanionAbsent`: Asserts `embedded_id3` is selected when companion is absent.
  - `f12_03_usltFrame_decodesUtf8Body`: Asserts payload decoding of ID3 `USLT` with null-terminated descriptor.
  - `f12_04_cacheHasHighestPriority`: Asserts local cache has top priority.
  - `f12_05_offlineGracefulFallbackWhenNoLyricsFound`: Asserts returns `""` and source `"none"`.
  - Defines `OfflineLyricsResolver` as an object within the test class.
- `GobeatsTier2BoundaryCornerTest.kt:642–695`:
  - `f12_b01_malformedLrcTimestamps_parsedAsPlainLyrics`: Verifies `LrcParser.parse` gracefully falls back to plain unsynced lines when timestamps are malformed.
  - `f12_b02_emptyLyricsString_returnsEmptyData`: Verifies empty string returns `LyricsData.EMPTY` with `source = "none"`.
  - `f12_b03_usltFrameShorterThanHeader_returnsEmpty`: Verifies payload `< 5` bytes returns `""`.
  - `f12_b04_lyricsStartingAtZeroMs_matchedAtZeroPlaybackPosition`: Verifies `LrcParser.getActiveLineIndex(..., 0L) == 0`.
  - `f12_b05_networkFailsDuringOnlineFallback_returnsEmptyCleanly`: Verifies exceptions from `onlineLyricsFetcher` are propagated when invoked.
- `GobeatsTier3CrossFeatureTest.kt:264–285`:
  - `cross_09_folderBrowsingDetectsCompanionLrcForFolderTracks`: Tests companion `.lrc` resolution in folder browsing context.
- `GobeatsTier4RealWorldWorkflowsTest.kt:102–126`:
  - `workflow_02_audiophileLosslessFlacLibraryAndTagEditing`: Step 3 asserts `LrcParser.parse(companionLrcContent, source = "companion")` parses timestamps and sets `isSynced = true`.

---

## 2. Logic Chain

1. **Resolution Priority and Order**:
   - Observations 1.1 and 1.4 prove that both the tests (`f12_01`, `f12_04`, `f12_05`) and the existing repository use this exact priority chain:
     $$\text{Cache / In-App Storage} \longrightarrow \text{Companion } \texttt{.lrc} \longrightarrow \text{Embedded ID3 / FLAC / M4A} \longrightarrow \text{Remote LRCLIB} \longrightarrow \text{Empty}$$
   - Any offline source (Companion or Embedded) takes precedence over remote LRCLIB.
   - User-provided external `.lrc` file takes precedence over embedded tags (because the user intentionally placed a companion file to supersede internal tags).
   - Local in-memory or persisted in-app cache takes precedence over everything to avoid repeated file I/O or network requests.

2. **Companion Discovery via MediaStore `DATA` Path**:
   - Observation 1.1 revealed that `track.fileUri` often has scheme `"content"`.
   - Observation 1.3 demonstrated the tested solution in `SubtitleDetector.kt`: query `MediaStore.MediaColumns.DATA` (`_data`) via `ContentResolver`.
   - Once the raw file path is resolved (e.g. `/storage/emulated/0/Music/Album/01 - Song.flac`), check `File(parentDir, "$baseName.lrc")`, case-insensitive directory match (`$baseName.LRC`), and fallback `$baseName.txt`.
   - This ensures 100% of offline tracks automatically load adjacent lyrics without user intervention.

3. **Embedded ID3 `USLT` & `SYLT` Extraction**:
   - Observation 1.1 showed `MediaMetadataRetriever` is insufficient.
   - Observation 1.2 showed `Id3TagWriter` lacks read support.
   - An `Id3TagReader` is needed to:
     a. Validate ID3v2 header (`"ID3"`, version 2.3 or 2.4, synchsafe tag length).
     b. Iterate frames:
        - `SYLT` (Synchronized Lyrics): Parse encoding, 3-byte language, 1-byte timestamp format (milliseconds), 1-byte content type, descriptor, and series of null-terminated text tokens with 4-byte big-endian timestamps. Construct `LyricLine` list with `isSynced = true`.
        - `USLT` (Unsynchronized Lyrics): Parse encoding byte (0x00=ISO-8859-1, 0x01=UTF-16 with BOM, 0x02=UTF-16BE, 0x03=UTF-8), skip 3-byte language code, find null descriptor delimiter (1 byte for 8-bit, 2 bytes for 16-bit), and decode remaining bytes. If content contains LRC timestamps (`[mm:ss.xx]`), `LrcParser.parse` yields synced lyrics.
   - If both exist in an MP3, prefer `SYLT` (synchronized) over `USLT` (unsynchronized).

4. **Embedded FLAC & M4A Extraction**:
   - For FLAC: Re-use `FlacTagWriter.readMetadataBlocks` and `FlacTagWriter.parseVorbisComments`. Look up keys `LYRICS`, `UNSYNCEDLYRICS`, `SYNCEDLYRICS`, `UNSYNCED LYRICS`.
   - For M4A: Read MP4 atoms to locate `moov/udta/meta/ilst/©lyr/data`, extract UTF-8 payload.
   - Run extracted text through `LrcParser.parse(text, source = "embedded")`.

5. **Interface Contract Alignment**:
   - Define `typealias SyncedLyrics = LyricsData` in `LrcParser.kt` or `LyricsRepository.kt`.
   - Implement `suspend fun getOfflineLyrics(track: Track): LyricsData?` in `LyricsRepository`.
   - Have `getLyricsForTrack(track: Track)` invoke `getOfflineLyrics(track)` first; if absent, invoke `LrclibClient.fetchLyrics(...)` with graceful offline fallback.

---

## 3. Caveats

1. **Android 11+ (API 30+) Scoped Storage Permissions**: Querying `MediaStore.MediaColumns.DATA` for audio tracks indexed in the user's MediaStore is fully supported on Android 10–14 for read-only path inspection. However, if an audio file is opened strictly via an opaque SAF URI without a direct filesystem path, `openInputStream(uri)` must be used to read embedded ID3/FLAC frames directly from the stream. The proposed `Id3TagReader` and `EmbeddedLyricsExtractor` accept both `File` and `InputStream` to guarantee 100% coverage.
2. **Charset Detection in Malformed Tags**: Some legacy MP3 encoders write UTF-8 in `USLT` while claiming ISO-8859-1 in byte 0. The decoder should attempt the declared charset and fallback cleanly to UTF-8 without throwing exceptions.
3. **No Caveats on Test Invalidation**: All existing 211 unit tests in `GobeatsTier1FeatureCoverageTest`, `GobeatsTier2BoundaryCornerTest`, `GobeatsTier3CrossFeatureTest`, and `GobeatsTier4RealWorldWorkflowsTest` will remain 100% green.

---

## 4. Conclusion & Recommendations for Worker

The worker agent should execute the following 5 concrete steps:

### Step 1: Create `Id3TagReader.kt`
- Path: `app/src/main/java/com/example/gobeats/core/media/tag/Id3TagReader.kt` (or `com/example/gobeats/core/media/Id3TagReader.kt`)
- Responsibilities:
  - `parseId3v2Tag(inputStream: InputStream): Map<String, List<ByteArray>>`
  - `parseUsltFrame(framePayload: ByteArray): String` (100% compatible with `OfflineLyricsResolver.parseUsltFrame`)
  - `parseSyltFrame(framePayload: ByteArray): LyricsData?`
  - `extractLyricsFromMp3(inputStream: InputStream): LyricsData?` (checks `SYLT` first, then `USLT`)

### Step 2: Implement Multi-Format Embedded Lyrics Extractor
- Support MP3 (`Id3TagReader`), FLAC (`FlacTagWriter.parseVorbisComments`), and M4A (`ilst` `©lyr`).
- Fallback gracefully to `MediaMetadataRetriever` if custom binary container parsing yields nothing.

### Step 3: Enhance `LyricsRepository.kt`
- Add `resolveFilePath(context: Context, track: Track): String?`:
  - Handles `file://...` and raw `/...` paths.
  - Queries `MediaStore.MediaColumns.DATA` for `content://...` URIs.
- Add `findCompanionLyrics(context: Context, track: Track): LyricsData?`:
  - Discovers `<song>.lrc` (case-insensitive) and `<song>.txt` in the track's directory.
  - Sets `source = "companion"`.
- Add `suspend fun getOfflineLyrics(track: Track): LyricsData?`:
  - Resolves: Cache (`source = "local"`) $\to$ Companion (`source = "companion"`) $\to$ Embedded (`source = "embedded"`).
  - Returns `null` if no offline lyrics exist.
- Add `typealias SyncedLyrics = LyricsData`.
- Update `getLyricsForTrack(track: Track)`:
  - Calls `getOfflineLyrics(track)` first.
  - If null, queries `LrclibClient.fetchLyrics(...)`. On success, caches to disk and returns with `source = "lrclib"`.
  - On network failure or no match, returns `LyricsData.EMPTY`.

### Step 4: Expose `OfflineLyricsResolver` in Core Module
- Path: `app/src/main/java/com/example/gobeats/core/lyrics/OfflineLyricsResolver.kt`
- Implements:
  ```kotlin
  object OfflineLyricsResolver {
      fun resolveLyrics(
          cacheLyrics: String?,
          companionLyrics: String?,
          embeddedId3Lyrics: String?,
          networkAvailable: Boolean,
          onlineLyricsFetcher: () -> String?
      ): Pair<String, String>

      fun parseUsltFrame(framePayload: ByteArray): String
  }
  ```
- Ensures production code and test harnesses share the exact same resolution contract.

### Step 5: Update UI Badge in `SyncedLyricsView.kt`
- Path: `app/src/main/java/com/example/gobeats/feature/nowplaying/SyncedLyricsView.kt`
- Update badge label logic (lines 165–169):
  ```kotlin
  text = when {
      lyricsData?.source == "lrclib" && lyricsData?.isSynced == true -> "LRCLIB • SYNCED"
      lyricsData?.source == "companion" && lyricsData?.isSynced == true -> "LOCAL LRC • SYNCED"
      lyricsData?.source == "embedded" && lyricsData?.isSynced == true -> "EMBEDDED • SYNCED"
      lyricsData?.source == "embedded" -> "EMBEDDED"
      lyricsData?.isSynced == true -> "SYNCED"
      else -> "PLAIN"
  }
  ```

---

## 5. Verification Method

To independently verify the implementation:

1. **Tier 1 Unit Test Verification**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier1FeatureCoverageTest.f12*"
   ```
   Must pass all 5 tests (`f12_01` through `f12_05`).

2. **Tier 2 Boundary Test Verification**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier2BoundaryCornerTest.f12*"
   ```
   Must pass all 5 tests (`f12_b01` through `f12_b05`).

3. **Tier 3 & 4 Cross-Feature and Workflow Verification**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier3CrossFeatureTest.cross_09*"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier4RealWorldWorkflowsTest.workflow_02*"
   ```

4. **Full Test Suite & Compilation**:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   .\gradlew.bat compileDebugKotlin
   ```
   Must build cleanly with 0 compilation errors and 100% green test suite (all 211+ tests passing).
