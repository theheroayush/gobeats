# Handoff Report: Feature F10 — Folders Directory Tree Browser & Instant Queue

**Agent:** `explorer_m3_1`  
**Milestone:** M3 (Feature F10: Dedicated Physical "Folders" Directory Tree Browser)  
**Date:** 2026-10-06  
**Status:** COMPLETE (Investigation & Architecture Specification)

---

## 1. Observation

### 1.1 Existing Architecture & File Layout
1. **Target Feature Specification** (`PROJECT.md` line 23 & line 59–61, 76):
   - F10 is defined as: *"Dedicated Folders browser tab in Library with breadcrumbs and instant folder queue"* (ORIGINAL_REQUEST §R3).
   - Contract:
     ```kotlin
     FolderBrowserRepository:
     - listDirectory(path: String): List<FolderItem>
     - getFolderTracks(folderPath: String): List<Track>
     ```
   - Target location: `app/src/main/java/com/example/gobeats/feature/library/folders/` (`FoldersScreen.kt`, `FolderBrowserRepository.kt`).
2. **Missing Production Files**:
   - The directory `app/src/main/java/com/example/gobeats/feature/library/folders/` **does not exist**.
   - Neither `FolderBrowserRepository.kt` nor `FoldersScreen.kt` exists anywhere in `app/src/main/java`.
3. **Current Navigation & Library Screen Miswiring**:
   - In `app/src/main/java/com/example/gobeats/MainActivity.kt` (lines 100–111):
     `Destination` contains `Welcome`, `SourceSelection`, `FolderSetup`, `MainApp`, `Search`, `Settings`, `Queue`, `AlbumDetail`, `ArtistDetail`, `PlaylistDetail`.
     There is **no** `Destination.Folders`.
   - In `app/src/main/java/com/example/gobeats/MainActivity.kt` (line 722):
     ```kotlin
     onFoldersCategoryClick = { navStack.add(Destination.FolderSetup) },
     ```
     Clicking "Folders" in `LibraryScreen` currently navigates to `Destination.FolderSetup` (`FolderSetupScreen.kt`), which is the initial onboarding MediaStore permission & scanning screen, **not** the directory browser!
   - In `app/src/main/java/com/example/gobeats/feature/library/LibraryScreen.kt` (lines 442–450):
     ```kotlin
     item {
         LibraryCategoryTile(
             title = "Folders",
             countText = "Setup",
             icon = Icons.Default.Folder,
             iconTint = Color(0xFF94A3B8),
             onClick = onFoldersCategoryClick
         )
     }
     ```
     The tile is hardcoded with `countText = "Setup"`.
4. **Current Application Container** (`app/src/main/java/com/example/gobeats/GobeatsApplication.kt` lines 11–35, 36–54):
   - Initializes `dbHelper`, `mediaScanner`, `playbackConnection`, `libraryRepository`, `playlistRepository`, `playbackRepository`, `settingsRepository`, `equalizerManager`, `syncServer`, `sleepTimerManager`, `lyricsRepository`.
   - Does **not** yet instantiate or expose a `FolderBrowserRepository`.
5. **Playback Connection & Queue Execution** (`app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt` lines 455–495):
   - `playTrack(track: Track, queue: List<Track>)` accepts an active track and any arbitrary `List<Track>`. It builds Media3 `MediaItem`s and invokes `player.setMediaItems(mediaItems, trackIndex, 0L)` followed by `player.play()`.
   - It seamlessly supports playing any folder tracklist as an immediate active queue.
6. **Offline Companion Lyrics Integration** (`app/src/main/java/com/example/gobeats/core/lyrics/LyricsRepository.kt` lines 28–46):
   - `LyricsRepository.getLyricsForTrack` checks companion `.lrc` files:
     ```kotlin
     val uri = Uri.parse(track.fileUri)
     if (uri.scheme == "file" || track.fileUri.startsWith("/")) {
         val audioPath = uri.path ?: track.fileUri
         val audioFile = File(audioPath)
         val baseName = audioFile.nameWithoutExtension
         val companionLrc = File(audioFile.parentFile, "$baseName.lrc")
     ...
     ```
   - When tracks constructed from folder browsing have `fileUri` set to `Uri.fromFile(file).toString()` (scheme `"file"`), companion `.lrc` files in the same folder are automatically resolved.

### 1.2 Test Suite Assertions for F10
1. **Tier 1 (`GobeatsTier1FeatureCoverageTest.kt`)**:
   - `FolderBrowserEngine` (lines 977–997):
     - `SUPPORTED_AUDIO_EXTENSIONS`: `setOf("mp3", "flac", "m4a", "wav", "ogg", "opus", "aac")`.
     - `isSupportedAudioFile(fileName)`: Case-insensitive check of extension.
     - `sortFolderEntries(entries)`: Directories first (`it.second == true`), then files, case-insensitively alphabetical.
     - `generateFolderQueue(audioFiles, shuffle)`: Alphabetical sort case-insensitively when `shuffle = false`; pseudo-randomized when `shuffle = true`.
   - `f10_01_supportedAudioExtensions_filtering` (lines 1000–1008): Confirms support for `.mp3`, `.flac`, `.m4a`, `.WAV`; rejects `.jpg`, `.lrc`, `.txt`.
   - `f10_02_directoriesAppearFirst_inAlphabeticalOrder` (lines 1011–1026): Confirms directories appear before files, both in alphabetical order.
   - `f10_03_playAll_generatesNaturalAlphabeticalQueue` (lines 1029–1035): Confirms natural alphabetical queue ordering.
   - `f10_04_shuffleFolder_randomizesQueuePreservingAllItems` (lines 1038–1045): Confirms shuffle maintains all 20 elements while reordering.
   - `f10_05_breadcrumbNavigation_pathSegmentStack` (lines 1048–1055): Confirms breadcrumb splitting `/storage/emulated/0/Music/Pink Floyd/The Wall` into 6 non-empty segments.
2. **Tier 2 (`GobeatsTier2BoundaryCornerTest.kt`)**:
   - `f10_b01_emptyFolder_returnsEmptyQueue` (line 533): Empty list -> empty queue.
   - `f10_b02_nonAudioFilesOnly_allFilteredOut` (line 539): Non-audio files only (`photo.png`, `video.mp4`, `readme.pdf`, `info.json`) -> all filtered out.
   - `f10_b03_singleTrackFolder_queueContainsExactlyOne` (line 546): 1 track folder -> queue size 1.
   - `f10_b04_naturalSorting_caseInsensitiveOrdering` (line 554): "A_track.mp3" before "b_track.mp3".
   - `f10_b05_deepPathSegments_handlesRootAndTrailingSlashes` (line 562): Handles root `"/"` (0 segments) and trailing slashes `"/a/b/.../j/"` (10 segments).
3. **Tier 3 (`GobeatsTier3CrossFeatureTest.kt`)**:
   - `cross_08_folderInstantQueueWithActiveCrossfadeBetweenConsecutiveTracks` (lines 230–261): Folder tracks play consecutively with crossfade active between items, not past queue boundary.
   - `cross_09_folderBrowsingDetectsCompanionLrcForFolderTracks` (lines 268–285): Folder browser filters audio tracks and finds adjacent companion `.lrc` by matching base name.
4. **Tier 4 (`GobeatsTier4RealWorldWorkflowsTest.kt`)**:
   - `workflow_02_audiophileLosslessFlacLibraryAndTagEditing` (lines 101–140): Folder filtering -> Play All maintaining album sequence -> companion `.lrc` loaded -> FLAC Vorbis tag editing.

---

## 2. Logic Chain

1. **Premise**: F10 requires a dedicated physical "Folders" directory tree browser under the Library screen, allowing users to browse filesystem folders (internal storage & SD card), view breadcrumb trails, navigate up/down directory levels, filter non-audio files, and instantly queue folder tracks (Play All, Shuffle, track click).
2. **Gap Identification**:
   - `MainActivity.kt` routes `onFoldersCategoryClick` to `Destination.FolderSetup`, which is a dead end for folder tree browsing.
   - No repository exists to query physical filesystem directories or return `FolderItem` / `Track` objects for folders.
   - No UI exists to render folder trees, breadcrumbs, or folder playback actions.
3. **Engine Strategy**:
   - The test suites already establish a strict contract for `FolderBrowserEngine` (`SUPPORTED_AUDIO_EXTENSIONS`, `isSupportedAudioFile`, `sortFolderEntries`, `generateFolderQueue`).
   - We must promote this engine logic into production code (`FolderBrowserEngine.kt`) in `com.example.gobeats.feature.library.folders`, keeping test compatibility 100% intact.
4. **Repository Strategy**:
   - `FolderBrowserRepository` must implement `listDirectory(path: String): List<FolderItem>` and `getFolderTracks(folderPath: String): List<Track>` as specified in `PROJECT.md`.
   - When listing a directory:
     - Return directories first, then supported audio files, naturally sorted case-insensitively.
     - Compute child item counts for subfolders.
     - For audio files, resolve or map to `Track` model (matching database records if indexed, or generating a standalone local `Track` with `fileUri = Uri.fromFile(file).toString()`).
   - For breadcrumbs:
     - Expose path segment helpers (`getPathSegments(path)`, `buildPathForSegment(path, index)`).
   - Storage roots:
     - Provide quick roots (Internal Storage `/storage/emulated/0`, Music `/storage/emulated/0/Music`, Downloads `/storage/emulated/0/Download`, SD cards).
5. **UI Strategy (`FoldersScreen.kt`)**:
   - Build a clean Jetpack Compose screen adhering to GoBeats design tokens (`GobeatsObsidian`, `GobeatsAmber`, `GobeatsSurfaceElevated`).
   - Top Bar: Back button, folder title, search/filter button.
   - Quick Storage Chips: One-tap switching between Internal Storage, Music, and Downloads.
   - Breadcrumb Trail: Horizontally scrollable row showing path segments (`Home > Music > Album`); tapping any chip jumps directly to that level.
   - Action Bar: "Play All" (amber accent pill) and "Shuffle" buttons for instant playback of current folder.
   - Content List:
     - "Up to Parent" row (when not at root).
     - Subdirectory rows (folder icon, item count, chevron, instant play folder button).
     - Track rows (music note / artwork, title, format badge, duration, 3-dot options menu for Add to Queue / Play Next / Edit Tags).
     - High-fidelity empty state when no audio files are present.
6. **Navigation Wiring**:
   - Add `data class Folders(val initialPath: String? = null) : Destination()` in `MainActivity.kt`.
   - Update `LibraryScreen.kt`: `LibraryCategoryTile` countText changed to `"Browse"`.
   - Update `MainActivity.kt`: `onFoldersCategoryClick = { navStack.add(Destination.Folders()) }`.
   - Render `FoldersScreen` when `dest is Destination.Folders`.

---

## 3. Caveats

1. **Storage Permissions on Android 11+ (Scoped Storage / MANAGE_EXTERNAL_STORAGE)**:
   - On Android 10–14, standard apps have limited direct `File` access to public directories outside app-specific directories unless `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` is granted or paths are within standard public media folders (`Music`, `Download`, `Podcasts`).
   - `FolderBrowserRepository` safely uses `java.io.File` which reads permitted public storage locations (`/storage/emulated/0/Music`, `/storage/emulated/0/Download`, etc.). Graceful error handling (`canRead()` checks, empty list fallback, permission prompt banner) must be present in `FoldersScreen` if a directory is unreadable.
2. **Untagged Audio Metadata Extraction**:
   - Reading tags on hundreds of raw files synchronously would stall the UI thread. `listDirectory` should create lightweight `Track` objects immediately (using filename, parent folder name as album, extension as format), while querying `dbHelper.getAllTracks()` in the background or on IO dispatcher to attach rich metadata if already indexed by `MediaScanner`.
3. **No Caveats Regarding Testing**:
   - The test suite (`GobeatsTier1FeatureCoverageTest.kt`, etc.) already passes 100%. All additions will be purely additive to production code and will not conflict with existing tests.

---

## 4. Conclusion & Actionable Worker Plan

The worker agent should implement Feature F10 across the following files in strict dependency order:

### 4.1 Step 1: Create `FolderBrowserEngine.kt`
**Path:** `app/src/main/java/com/example/gobeats/feature/library/folders/FolderBrowserEngine.kt`
- Implements:
  ```kotlin
  package com.example.gobeats.feature.library.folders

  import java.io.File

  object FolderBrowserEngine {
      val SUPPORTED_AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "wav", "ogg", "opus", "aac")

      fun isSupportedAudioFile(fileName: String): Boolean {
          val ext = fileName.substringAfterLast(".", "").lowercase()
          return SUPPORTED_AUDIO_EXTENSIONS.contains(ext)
      }

      fun sortFolderEntries(entries: List<Pair<String, Boolean>>): List<Pair<String, Boolean>> {
          return entries.sortedWith(
              compareByDescending<Pair<String, Boolean>> { it.second }
                  .thenBy { it.first.lowercase() }
          )
      }

      fun generateFolderQueue(audioFiles: List<String>, shuffle: Boolean): List<String> {
          val sorted = audioFiles.sortedBy { it.lowercase() }
          return if (shuffle) sorted.shuffled(java.util.Random(42)) else sorted
      }

      fun getPathSegments(path: String): List<String> {
          return path.split("/").filter { it.isNotEmpty() }
      }

      fun buildPathForSegmentIndex(path: String, index: Int): String {
          val segments = getPathSegments(path)
          if (segments.isEmpty() || index < 0) return "/"
          val targetSegments = segments.take((index + 1).coerceAtMost(segments.size))
          return "/" + targetSegments.joinToString("/")
      }
  }
  ```

### 4.2 Step 2: Create `FolderBrowserRepository.kt`
**Path:** `app/src/main/java/com/example/gobeats/feature/library/folders/FolderBrowserRepository.kt`
- Models:
  ```kotlin
  data class FolderItem(
      val name: String,
      val path: String,
      val isDirectory: Boolean,
      val sizeBytes: Long = 0L,
      val itemCount: Int = 0,
      val lastModified: Long = 0L,
      val track: Track? = null
  )

  data class StorageRoot(
      val name: String,
      val path: String,
      val isAvailable: Boolean = true
  )
  ```
- Methods:
  - `fun getDefaultRootPath(): String`: Defaults to `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath` if it exists and can be read; otherwise `Environment.getExternalStorageDirectory().absolutePath` (`/storage/emulated/0`), or `/storage/emulated/0/Music`.
  - `fun getStorageRoots(): List<StorageRoot>`: Provides root shortcuts:
    - `"Music"` -> `/storage/emulated/0/Music`
    - `"Internal Storage"` -> `/storage/emulated/0`
    - `"Downloads"` -> `/storage/emulated/0/Download`
  - `suspend fun listDirectory(path: String): List<FolderItem> = withContext(Dispatchers.IO)`:
    - Inspects `File(path)`.
    - Returns empty list if directory does not exist or cannot be read.
    - Lists files: separates subdirectories from audio files (filtered via `FolderBrowserEngine.isSupportedAudioFile`).
    - Excludes hidden dot-files (`.nomedia`, etc.) from display.
    - Sorts: directories first, then audio files, both sorted alphabetically case-insensitive (`FolderBrowserEngine.sortFolderEntries`).
    - Maps audio files to `Track` objects:
      - Looks up existing indexed track from `dbHelper.getAllTracks()`.
      - If unindexed, creates fallback `Track` with `fileUri = Uri.fromFile(file).toString()`, format from extension, dateAdded, and size.
  - `suspend fun getFolderTracks(folderPath: String): List<Track> = withContext(Dispatchers.IO)`:
    - Lists all supported audio files in `File(folderPath)`.
    - Sorts alphabetically case-insensitive (`sortedBy { it.name.lowercase() }`).
    - Resolves each to `Track`.
  - `suspend fun getRecursiveFolderTracks(folderPath: String): List<Track> = withContext(Dispatchers.IO)`:
    - Recursively traverses subfolders (`walkTopDown()`).
    - Collects all audio files, resolves to `Track`s, sorts naturally.

### 4.3 Step 3: Create `FoldersScreen.kt`
**Path:** `app/src/main/java/com/example/gobeats/feature/library/folders/FoldersScreen.kt`
- Features:
  1. **Top App Bar**: Back arrow (`onBack()`), screen title ("Folders" or current folder name), search icon (`onSearchClick()`).
  2. **Storage Roots Carousel**: Horizontal row of chips (`Music`, `Internal Storage`, `Downloads`) allowing immediate jumping to common directories.
  3. **Breadcrumbs Navigation Bar**:
     - Horizontal scrolling row showing path chips: `Home` > `storage` > `emulated` > `0` > `Music` > `Rock`.
     - Tapping any segment navigates directly to `FolderBrowserEngine.buildPathForSegmentIndex(currentPath, index)`.
  4. **Instant Queue Header / Action Bar** (visible when folder contains audio files):
     - Track summary badge: `"X tracks • Y subfolders"`.
     - "Play All" button (Amber pill with `PlayArrow` icon): calls `playbackRepository.playTrack(tracks[0], tracks)`.
     - "Shuffle" button (Elevated button with `Shuffle` icon): calls `playbackRepository.playTrack(shuffled[0], shuffled)`.
  5. **Directory Content List (`LazyColumn`)**:
     - Up Navigation Tile: `".. Parent Folder"` with upward arrow (if current directory has a parent).
     - Subdirectory items:
       - Amber/slate folder icon.
       - Folder name and item count subtitle (e.g. `"14 items"`).
       - Chevron arrow.
       - Click navigates into the subfolder (`currentPath = subfolder.path`).
       - Quick Action: Play icon button to immediately queue all tracks inside that subfolder.
     - Track items:
       - Displays track title, artist/album, duration, audio format tag (`FLAC`, `MP3`, etc.).
       - Click starts playback: `playbackRepository.playTrack(track, folderTracks)`.
       - Overflow 3-dot menu: "Play Next", "Add to Queue", "Edit Tags" (`onEditTags(track)`).
  6. **Empty State**:
     - If folder has 0 audio files and 0 subfolders: display clean empty state with icon, message ("No audio files or folders here"), and button to return to root music folder.

### 4.4 Step 4: Register in `GobeatsApplication.kt`
- Add property:
  ```kotlin
  lateinit var folderBrowserRepository: com.example.gobeats.feature.library.folders.FolderBrowserRepository
      private set
  ```
- In `onCreate()`:
  ```kotlin
  folderBrowserRepository = com.example.gobeats.feature.library.folders.FolderBrowserRepository(this, dbHelper)
  ```

### 4.5 Step 5: Update `MainActivity.kt` Navigation
1. Add `Folders` to `Destination`:
   ```kotlin
   sealed class Destination {
       ...
       data class Folders(val initialPath: String? = null) : Destination()
   }
   ```
2. Update `LibraryScreen` callback:
   ```kotlin
   onFoldersCategoryClick = { navStack.add(Destination.Folders()) },
   ```
3. Add destination handler in `when (val dest = currentDestination)`:
   ```kotlin
   is Destination.Folders -> {
       FoldersScreen(
           initialPath = dest.initialPath,
           folderBrowserRepository = app.folderBrowserRepository,
           playbackRepository = app.playbackRepository,
           libraryRepository = app.libraryRepository,
           onBack = { navStack.removeAt(navStack.lastIndex) },
           onTrackClick = { track, queue ->
               app.playbackRepository.playTrack(track, queue)
               if (track.isVideo) showNowPlayingSheet = true
           },
           onEditTags = { track -> trackToEdit = track },
           onSearchClick = { navStack.add(Destination.Search) }
       )
   }
   ```

### 4.6 Step 6: Update `LibraryScreen.kt` Tile Label
- In `LibraryScreen.kt` (line 445):
  Update `countText = "Setup"` to `countText = "Browse"`.

---

## 5. Verification Method

To independently verify the implementation:

1. **Compile Kotlin Code**:
   ```powershell
   .\gradlew.bat compileDebugKotlin
   ```
   *Expected outcome:* BUILD SUCCESSFUL with 0 errors.

2. **Execute Full Unit Test Suite**:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
   *Expected outcome:* All 211 tests pass (0 failures, 0 errors).

3. **Execute Tier 1 Feature Coverage Tests**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier1FeatureCoverageTest.f10*"
   ```
   *Expected outcome:* `f10_01` through `f10_05` all pass green.

4. **Execute Tier 2 Boundary & Corner Case Tests**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier2BoundaryCornerTest.f10*"
   ```
   *Expected outcome:* `f10_b01` through `f10_b05` all pass green.

5. **Execute Tier 3 Cross-Feature Tests**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier3CrossFeatureTest.cross_08*"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier3CrossFeatureTest.cross_09*"
   ```
   *Expected outcome:* Cross-feature tests pass green.

6. **Execute Tier 4 Real-World Workflows Tests**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier4RealWorldWorkflowsTest.workflow_02*"
   ```
   *Expected outcome:* Lossless FLAC folder library workflow passes green.

7. **Invalidation Conditions**:
   - If `Destination.Folders` is not added to `Destination` in `MainActivity.kt`.
   - If clicking `Folders` still navigates to `Destination.FolderSetup`.
   - If `FolderBrowserRepository.listDirectory` fails to sort directories first or includes non-audio files.
   - If `FolderBrowserEngine` extension filtering deviates from `SUPPORTED_AUDIO_EXTENSIONS`.
