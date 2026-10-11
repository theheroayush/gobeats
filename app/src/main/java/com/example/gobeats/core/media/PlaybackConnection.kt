package com.example.gobeats.core.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.gobeats.core.database.GobeatsDatabaseHelper
import com.example.gobeats.core.model.DualSubtitleTrack
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.RepeatMode
import com.example.gobeats.core.model.Track
import com.example.gobeats.core.model.VideoAspectMode
import com.example.gobeats.core.model.VideoResumePrompt
import com.example.gobeats.core.playback.ProcessDeathPersistenceEngine
import com.example.gobeats.core.video.AiSubtitleEngine
import com.example.gobeats.core.video.OfflineSkipIntroDetector
import com.example.gobeats.core.video.SubtitleDetector
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackConnection(
    private val context: Context,
    private val dbHelper: GobeatsDatabaseHelper
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private var currentQueue: List<Track> = emptyList()
    private var positionJob: Job? = null
    private var playJob: Job? = null
    private var pendingPlay: Pair<Track, List<Track>>? = null
    private var baseTrackVolume: Float = 1.0f
    private var fadeInJob: Job? = null
    private var settingsRepo: com.example.gobeats.data.repository.SettingsRepository? = null

    private var lastTrackPlayedId: String? = null
    private var lastTrackStartTimestampMs: Long = 0L
    private val lyricsFetcher by lazy { com.example.gobeats.core.lyrics.LrclibLyricsFetcher(context) }

    val audioSessionId: StateFlow<Int> =
        (context.applicationContext as? com.example.gobeats.GobeatsApplication)?.equalizerManager?.audioSessionId
            ?: MutableStateFlow(0).asStateFlow()

    init {
        initController()
        try {
            settingsRepo = com.example.gobeats.data.repository.SettingsRepository(context)
            settingsRepo?.let { repo ->
                _playbackState.update {
                    it.copy(
                        crossfadeDurationSec = repo.settings.value.crossfadeDurationSec,
                        gaplessPlayback = repo.settings.value.gaplessPlayback
                    )
                }
                scope.launch {
                    repo.settings.collect { appSettings ->
                        _playbackState.update {
                            it.copy(
                                crossfadeDurationSec = appSettings.crossfadeDurationSec,
                                gaplessPlayback = appSettings.gaplessPlayback
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        scope.launch {
            _playbackState.collectLatest { state ->
                try {
                    com.example.gobeats.feature.widget.GobeatsAppWidgetProvider.notifyPlaybackChanged(
                        context,
                        state.currentTrack?.title,
                        state.currentTrack?.artistName,
                        state.isPlaying
                    )
                } catch (_: Exception) {}
            }
        }

        val app = context.applicationContext as? com.example.gobeats.GobeatsApplication
        if (app != null) {
            scope.launch {
                app.audioDeviceProfileManager.currentProfile.collectLatest { profile ->
                    _playbackState.update {
                        it.copy(
                            currentAudioDeviceProfile = profile,
                            avDelayMs = profile.avDelayMs
                        )
                    }
                }
            }
            scope.launch {
                app.dialogueBoostProcessor.mode.collectLatest { boostMode ->
                    _playbackState.update {
                        it.copy(dialogueBoostMode = boostMode)
                    }
                }
            }
        }
    }


    private fun initController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, GobeatsMediaSessionService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            setupPlayerListener()
            val pending = pendingPlay
            if (pending != null) {
                pendingPlay = null
                playTrack(pending.first, pending.second)
            } else {
                restorePlaybackStateIfEmpty()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        val player = controller ?: return

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("PlaybackConnection", "Playback error: ${error.errorCodeName} (${error.errorCode})", error)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                    persistCurrentVideoPosition()
                    persistCurrentPlaybackState()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                persistCurrentVideoPosition()
                persistCurrentPlaybackState()
                fadeInJob?.cancel()
                val crossfadeSec = _playbackState.value.crossfadeDurationSec
                val mediaId = mediaItem?.mediaId ?: return
                val currentTrack = currentQueue.find { it.id == mediaId }
                val currentIndex = currentQueue.indexOf(currentTrack).coerceAtLeast(0)

                _playbackState.update {
                    it.copy(
                        currentTrack = currentTrack,
                        queueIndex = currentIndex,
                        durationMs = player.duration.coerceAtLeast(0L),
                        isVideoMode = currentTrack?.isVideo ?: false,
                        videoWidth = currentTrack?.videoWidth ?: it.videoWidth,
                        videoHeight = currentTrack?.videoHeight ?: it.videoHeight
                    )
                }

                // Log telemetry for previously active track
                val prevTrackId = lastTrackPlayedId
                if (prevTrackId != null && lastTrackStartTimestampMs > 0) {
                    val durationListened = System.currentTimeMillis() - lastTrackStartTimestampMs
                    val isSkipped = durationListened in 1000L..15000L
                    scope.launch(Dispatchers.IO) {
                        dbHelper.recordPlaybackTelemetry(
                            trackId = prevTrackId,
                            listenDurationMs = durationListened,
                            isCompleted = false,
                            isSkipped = isSkipped
                        )
                    }
                }

                if (currentTrack != null) {
                    lastTrackPlayedId = currentTrack.id
                    lastTrackStartTimestampMs = System.currentTimeMillis()
                    lyricsFetcher.prefetchLyricsAsync(currentTrack)

                    if (currentTrack.isVideo) {
                        checkAndShowVideoResumePrompt(currentTrack.id)
                        val introSeg = OfflineSkipIntroDetector.detectIntroSegment(currentTrack)
                        _playbackState.update {
                            it.copy(
                                activeSkipIntroSegment = introSeg,
                                isSkipIntroVisible = OfflineSkipIntroDetector.isSkipIntroActive(0L, introSeg)
                            )
                        }
                    } else {
                        _playbackState.update {
                            it.copy(
                                resumePrompt = null,
                                activeSkipIntroSegment = null,
                                isSkipIntroVisible = false,
                                activePrimarySubtitleText = null,
                                activeSecondarySubtitleText = null
                            )
                        }
                    }
                    scope.launch(Dispatchers.IO) {
                        dbHelper.recordTrackPlayed(currentTrack.id)
                        val eqMgr = (context.applicationContext as? com.example.gobeats.GobeatsApplication)?.equalizerManager
                        val targetVolume = if (eqMgr != null) {
                            eqMgr.computeTrackReplayGain(currentTrack).linearVolume
                        } else {
                            1.0f
                        }
                        baseTrackVolume = targetVolume

                        scope.launch(Dispatchers.Main) {
                            if (crossfadeSec > 0 && !currentTrack.isVideo) {
                                startFadeInRamp(targetVolume, crossfadeSec)
                            } else {
                                controller?.volume = targetVolume
                                _playbackState.update { it.copy(isCrossfadeActive = false) }
                            }
                        }
                    }
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                _playbackState.update { it.copy(playbackSpeed = playbackParameters.speed) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _playbackState.update {
                        it.copy(durationMs = player.duration.coerceAtLeast(0L))
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    val finishedId = lastTrackPlayedId
                    if (finishedId != null) {
                        val durationListened = System.currentTimeMillis() - lastTrackStartTimestampMs
                        scope.launch(Dispatchers.IO) {
                            dbHelper.recordPlaybackTelemetry(
                                trackId = finishedId,
                                listenDurationMs = durationListened,
                                isCompleted = true,
                                isSkipped = false
                            )
                        }
                    }
                    onTrackEndedListener?.invoke()
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    _playbackState.update {
                        it.copy(
                            videoWidth = videoSize.width,
                            videoHeight = videoSize.height
                        )
                    }
                }
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val audioList = mutableListOf<String>()
                val subtitleList = mutableListOf<String>()
                var selectedAudioIdx = 0
                var selectedSubtitleIdx = -1

                var nativeWidth = 0
                var nativeHeight = 0
                var nativeFps = 0f
                var nativeBitrate = 0

                for (group in tracks.groups) {
                    val trackType = group.type
                    if (trackType == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val lang = format.language?.uppercase() ?: "AUDIO ${audioList.size + 1}"
                            val label = format.label ?: lang
                            val codec = format.sampleMimeType?.substringAfterLast('/')?.uppercase() ?: ""
                            val display = if (codec.isNotBlank()) "$label ($codec)" else label
                            audioList.add(display)
                            if (group.isTrackSelected(i)) {
                                selectedAudioIdx = audioList.size - 1
                            }
                        }
                    } else if (trackType == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val lang = format.language?.uppercase() ?: "SUB ${subtitleList.size + 1}"
                            val label = format.label ?: lang
                            subtitleList.add(label)
                            if (group.isTrackSelected(i)) {
                                selectedSubtitleIdx = subtitleList.size - 1
                            }
                        }
                    } else if (trackType == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            if (format.width > nativeWidth) {
                                nativeWidth = format.width
                                nativeHeight = format.height
                                nativeFps = format.frameRate
                                nativeBitrate = format.bitrate
                            }
                        }
                    }
                }

                if (nativeWidth == 0) {
                    nativeWidth = _playbackState.value.videoWidth
                    nativeHeight = _playbackState.value.videoHeight
                }

                val videoGroups = tracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO }
                val totalVideoFormats = videoGroups.sumOf { it.length }
                val currentTrackUri = _playbackState.value.currentTrack?.fileUri ?: ""
                val isAdaptiveStream = currentTrackUri.endsWith(".m3u8", ignoreCase = true) ||
                        currentTrackUri.endsWith(".mpd", ignoreCase = true) ||
                        currentTrackUri.contains("m3u8", ignoreCase = true) ||
                        currentTrackUri.contains("mpd", ignoreCase = true)
                val isSingleRenditionLocal = totalVideoFormats <= 1 && !isAdaptiveStream

                val currentSelectedQuality = _playbackState.value.selectedVideoQualityId
                val qualityList = mutableListOf<com.example.gobeats.core.model.VideoQualityOption>()

                val autoLabel = if (nativeHeight > 0) {
                    val fpsTag = if (nativeFps >= 45f) "60" else ""
                    val resTag = when {
                        nativeHeight >= 2160 -> "4K"
                        nativeHeight >= 1440 -> "1440p$fpsTag"
                        nativeHeight >= 1080 -> "1080p$fpsTag"
                        nativeHeight >= 720 -> "720p$fpsTag"
                        else -> "${nativeHeight}p"
                    }
                    "Auto ($resTag)"
                } else {
                    "Auto (Recommended)"
                }

                qualityList.add(
                    com.example.gobeats.core.model.VideoQualityOption(
                        id = "auto",
                        label = autoLabel,
                        resolutionLabel = if (nativeWidth > 0) "${nativeWidth}x${nativeHeight}" else "Native",
                        frameRate = nativeFps,
                        bitrate = nativeBitrate,
                        isSelected = currentSelectedQuality == "auto" || (isSingleRenditionLocal && currentSelectedQuality !in listOf("auto", "original")),
                        isAuto = true
                    )
                )

                if (isSingleRenditionLocal) {
                    // For single-rendition local files: provide Native/Original option without downscaling constraints
                    qualityList.add(
                        com.example.gobeats.core.model.VideoQualityOption(
                            id = "original",
                            label = if (nativeHeight > 0) "Original (${nativeHeight}p - Local File)" else "Original (Native)",
                            resolutionLabel = if (nativeWidth > 0) "${nativeWidth}x${nativeHeight}" else "Native",
                            frameRate = nativeFps,
                            bitrate = nativeBitrate,
                            isSelected = currentSelectedQuality == "original"
                        )
                    )
                } else {
                    val ladder = listOf(
                        Triple("2160p", "4K (2160p)", 2160 to 3840),
                        Triple("1440p", "1440p (QHD)", 1440 to 2560),
                        Triple("1080p", "1080p (FHD)", 1080 to 1920),
                        Triple("720p", "720p (HD)", 720 to 1280),
                        Triple("480p", "480p (SD)", 480 to 854),
                        Triple("360p", "360p (Data Saver)", 360 to 640)
                    )

                    for ((id, label, dims) in ladder) {
                        if (nativeHeight <= 0 || dims.first <= nativeHeight || id == "1080p" || id == "720p" || id == "480p") {
                            qualityList.add(
                                com.example.gobeats.core.model.VideoQualityOption(
                                    id = id,
                                    label = label,
                                    resolutionLabel = "${dims.second}x${dims.first}",
                                    maxHeight = dims.first,
                                    maxWidth = dims.second,
                                    isSelected = currentSelectedQuality == id
                                )
                            )
                        }
                    }
                }

                _playbackState.update {
                    it.copy(
                        audioTracks = audioList,
                        selectedAudioTrackIndex = selectedAudioIdx,
                        subtitleTracks = subtitleList,
                        selectedSubtitleTrackIndex = selectedSubtitleIdx,
                        videoQualityOptions = qualityList,
                        isSingleRenditionLocalFile = isSingleRenditionLocal
                    )
                }
            }
        })
    }

    private fun startFadeInRamp(targetVolume: Float, crossfadeSec: Int) {
        fadeInJob?.cancel()
        val player = controller ?: return
        player.volume = 0.0f
        _playbackState.update { it.copy(isCrossfadeActive = true) }

        fadeInJob = scope.launch {
            val rampDurationMs = (crossfadeSec * 1000L).coerceAtMost(2500L)
            val stepIntervalMs = 50L
            val totalSteps = (rampDurationMs / stepIntervalMs).toInt().coerceAtLeast(1)

            for (step in 1..totalSteps) {
                delay(stepIntervalMs)
                if (!isActive) break
                val progress = step.toFloat() / totalSteps.toFloat()
                val (_, volIn) = com.example.gobeats.core.audio.CrossfadeEngine.computeEqualPowerVolumes(progress)
                player.volume = (targetVolume * volIn).coerceIn(0f, 1f)
            }
            player.volume = targetVolume
            _playbackState.update { it.copy(isCrossfadeActive = false) }
        }
    }

    var onTrackEndedListener: (() -> Unit)? = null

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            var tick = 0
            while (isActive) {
                controller?.let { player ->
                    // Audiophile Crossfade evaluation
                    if (!_playbackState.value.isVideoMode) {
                        val crossfadeSec = _playbackState.value.crossfadeDurationSec
                        val queueIdx = _playbackState.value.queueIndex
                        val qSize = currentQueue.size
                        val repMode = _playbackState.value.repeatMode
                        val pos = player.currentPosition
                        val dur = player.duration

                        val shouldFade = com.example.gobeats.core.audio.CrossfadeEngine.shouldTriggerCrossfade(
                            currentPosMs = pos,
                            durationMs = dur,
                            crossfadeSec = crossfadeSec,
                            queueIndex = queueIdx,
                            queueSize = qSize,
                            repeatMode = repMode
                        )

                        if (shouldFade && fadeInJob?.isActive != true) {
                            val progress = com.example.gobeats.core.audio.CrossfadeEngine.calculateProgress(pos, dur, crossfadeSec)
                            val (volOut, _) = com.example.gobeats.core.audio.CrossfadeEngine.computeEqualPowerVolumes(progress)
                            player.volume = (baseTrackVolume * volOut).coerceIn(0f, 1f)
                            if (!_playbackState.value.isCrossfadeActive) {
                                _playbackState.update { it.copy(isCrossfadeActive = true) }
                            }
                        } else if (!shouldFade && fadeInJob?.isActive != true && _playbackState.value.isCrossfadeActive) {
                            player.volume = baseTrackVolume
                            _playbackState.update { it.copy(isCrossfadeActive = false) }
                        }
                    }

                    tick++
                    if (tick % 20 == 0 && player.isPlaying) {
                        persistCurrentPlaybackState()
                    }
                    if (tick % 16 == 0 && _playbackState.value.isVideoMode) {
                        val currentTrack = _playbackState.value.currentTrack
                        if (currentTrack != null) {
                            val pos = player.currentPosition
                            val dur = player.duration
                            if (pos > 5000L && dur > 10000L) {
                                if (pos >= dur - 10000L) {
                                    scope.launch(Dispatchers.IO) {
                                        dbHelper.clearVideoPosition(currentTrack.id)
                                    }
                                } else {
                                    scope.launch(Dispatchers.IO) {
                                        dbHelper.saveVideoPosition(currentTrack.id, pos, dur)
                                    }
                                }
                            }
                        }
                    }

                    var liveStats: com.example.gobeats.core.model.VideoPlaybackStats? = null
                    if (_playbackState.value.isVideoMode && tick % 4 == 0) {
                        var vFormat: androidx.media3.common.Format? = null
                        var aFormat: androidx.media3.common.Format? = null

                        for (group in player.currentTracks.groups) {
                            if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                                for (i in 0 until group.length) {
                                    if (group.isTrackSelected(i)) {
                                        vFormat = group.getTrackFormat(i)
                                        break
                                    }
                                }
                            } else if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                                for (i in 0 until group.length) {
                                    if (group.isTrackSelected(i)) {
                                        aFormat = group.getTrackFormat(i)
                                        break
                                    }
                                }
                            }
                        }

                        val w = if ((vFormat?.width ?: 0) > 0) vFormat!!.width else _playbackState.value.videoWidth
                        val h = if ((vFormat?.height ?: 0) > 0) vFormat!!.height else _playbackState.value.videoHeight
                        val fps = if ((vFormat?.frameRate ?: 0f) > 0f) vFormat!!.frameRate else 0f
                        val fpsStr = if (fps > 0f) "@ ${"%.1f".format(fps)} fps" else ""
                        val resStr = if (w > 0 && h > 0) "${w}x${h} $fpsStr".trim() else "Native"
                        val vCodec = vFormat?.sampleMimeType?.substringAfterLast('/')?.uppercase() ?: "AVC"
                        val bitrateVal = vFormat?.bitrate ?: 0
                        val vBitrate = if (bitrateVal > 0) bitrateVal / 1000 else 0
                        val aCodec = aFormat?.sampleMimeType?.substringAfterLast('/')?.uppercase() ?: "AAC"
                        val sampleRateVal = aFormat?.sampleRate ?: 48000
                        val aSampleRate = (if (sampleRateVal > 0) sampleRateVal else 48000) / 1000f
                        val aChannels = when (aFormat?.channelCount) {
                            6 -> "5.1 Surround"
                            2 -> "Stereo"
                            1 -> "Mono"
                            else -> "${aFormat?.channelCount ?: 2}ch"
                        }
                        val bufferMs = player.totalBufferedDuration.coerceAtLeast(0L)

                        liveStats = com.example.gobeats.core.model.VideoPlaybackStats(
                            resolution = resStr,
                            videoCodec = "$vCodec ${vFormat?.codecs ?: ""}".trim(),
                            colorStandard = if (h >= 2160) "BT.2020 HDR" else "BT.709 SDR",
                            videoBitrateKbps = vBitrate,
                            decoderName = "Hardware Accelerated MediaCodec",
                            audioCodec = "$aCodec ${"%.1f".format(aSampleRate)} kHz ($aChannels)",
                            droppedFrames = 0L,
                            totalFrames = (player.currentPosition * 60L / 1000L).coerceAtLeast(0L),
                            bufferHealthMs = bufferMs
                        )
                    }

                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val introSeg = _playbackState.value.activeSkipIntroSegment
                    val isIntroActive = if (introSeg != null) {
                        OfflineSkipIntroDetector.isSkipIntroActive(pos, introSeg)
                    } else false

                    val primTrack = _playbackState.value.primarySubtitleTrack
                    val secTrack = _playbackState.value.secondarySubtitleTrack
                    val primText = primTrack?.cues?.find { pos in it.startMs..it.endMs }?.text
                    val secText = secTrack?.cues?.find { pos in it.startMs..it.endMs }?.text

                    _playbackState.update {
                        it.copy(
                            currentPositionMs = pos,
                            durationMs = player.duration.coerceAtLeast(0L),
                            videoStats = liveStats ?: it.videoStats,
                            isSkipIntroVisible = isIntroActive,
                            activePrimarySubtitleText = primText,
                            activeSecondarySubtitleText = secText
                        )
                    }
                }
                delay(250)
            }
        }
    }

    private fun persistCurrentVideoPosition() {
        val player = controller ?: return
        val currentTrack = _playbackState.value.currentTrack ?: return
        if (_playbackState.value.isVideoMode && currentTrack.isVideo) {
            val pos = player.currentPosition
            val dur = player.duration
            if (pos > 5000L && dur > 10000L) {
                if (pos >= dur - 10000L) {
                    scope.launch(Dispatchers.IO) {
                        dbHelper.clearVideoPosition(currentTrack.id)
                    }
                } else {
                    scope.launch(Dispatchers.IO) {
                        dbHelper.saveVideoPosition(currentTrack.id, pos, dur)
                    }
                }
            }
        }
    }

    fun persistCurrentPlaybackState() {
        val player = controller ?: return
        val currentTrack = _playbackState.value.currentTrack
        val queueIds = currentQueue.map { it.id }
        val pos = player.currentPosition.coerceAtLeast(0L)
        val isPlaying = player.isPlaying
        scope.launch(Dispatchers.IO) {
            dbHelper.savePlaybackState(
                trackId = currentTrack?.id,
                positionMs = pos,
                queueIds = queueIds,
                isPlaying = isPlaying
            )
        }
    }

    private fun restorePlaybackStateIfEmpty() {
        val player = controller ?: return
        if (player.mediaItemCount == 0) {
            scope.launch(Dispatchers.IO) {
                val savedState = dbHelper.getPlaybackState()
                if (savedState != null && savedState.queueTrackIds.isNotEmpty()) {
                    val dbTracks = dbHelper.getTracksByIds(savedState.queueTrackIds)
                    val restoredTracks = ProcessDeathPersistenceEngine.sortTracksByQueueIds(dbTracks, savedState.queueTrackIds)
                    if (restoredTracks.isNotEmpty()) {
                        val targetIndex = restoredTracks.indexOfFirst { it.id == savedState.currentTrackId }.coerceAtLeast(0)
                        val activeTrack = restoredTracks.getOrNull(targetIndex)
                        val maxWindowRadius = 25
                        val windowStart = kotlin.math.max(0, targetIndex - maxWindowRadius)
                        val windowEnd = kotlin.math.min(restoredTracks.size, targetIndex + maxWindowRadius + 1)
                        val windowedTracks = restoredTracks.subList(windowStart, windowEnd)
                        val mediaItems = windowedTracks.map { buildMediaItem(it) }
                        val windowIndex = targetIndex - windowStart
                        val safePosition = savedState.positionMs.coerceAtLeast(0L)

                        withContext(Dispatchers.Main) {
                            val activePlayer = controller ?: return@withContext
                            if (activePlayer.mediaItemCount == 0) {
                                currentQueue = restoredTracks
                                activePlayer.setMediaItems(mediaItems, windowIndex, safePosition)
                                activePlayer.prepare()
                                activePlayer.playWhenReady = false // strictly paused on process death restoration
                                _playbackState.update {
                                    it.copy(
                                        currentTrack = activeTrack,
                                        queue = restoredTracks,
                                        queueIndex = targetIndex,
                                        currentPositionMs = safePosition,
                                        durationMs = activeTrack?.durationMs ?: 0L,
                                        isPlaying = false,
                                        isVideoMode = activeTrack?.isVideo ?: false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkAndShowVideoResumePrompt(trackId: String) {
        scope.launch {
            val savedPos = dbHelper.getVideoPosition(trackId)
            if (savedPos != null && savedPos.positionMs > 5000L && (savedPos.durationMs <= 0L || savedPos.positionMs < savedPos.durationMs - 10000L)) {
                _playbackState.update {
                    it.copy(
                        resumePrompt = VideoResumePrompt(
                            videoId = trackId,
                            positionMs = savedPos.positionMs,
                            durationMs = savedPos.durationMs
                        )
                    )
                }
            } else {
                _playbackState.update { it.copy(resumePrompt = null) }
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
    }

    private fun buildMediaItem(track: Track): MediaItem {
        val parsedUri = Uri.parse(track.fileUri)
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistName)
            .setAlbumTitle(track.albumTitle)
            .setArtworkUri(if (track.artworkUri != null) Uri.parse(track.artworkUri) else null)
            .build()

        val requestMetadata = MediaItem.RequestMetadata.Builder()
            .setMediaUri(parsedUri)
            .build()

        val subtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
        if (track.isVideo) {
            val detected = SubtitleDetector.detectSubtitles(context, track)
            subtitleConfigs.addAll(detected.map { it.toSubtitleConfiguration() })
        }

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(parsedUri)
            .setRequestMetadata(requestMetadata)
            .setMediaMetadata(metadata)
            .setSubtitleConfigurations(subtitleConfigs)
            .build()
    }

    fun playTrack(track: Track, queue: List<Track>) {
        val player = controller
        val trackIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        // Optimistically update UI immediately for instant tactile response
        currentQueue = queue
        _playbackState.update {
            it.copy(
                currentTrack = track,
                queue = queue,
                queueIndex = trackIndex,
                isPlaying = true,
                isVideoMode = track.isVideo,
                videoWidth = track.videoWidth,
                videoHeight = track.videoHeight
            )
        }

        if (player == null) {
            pendingPlay = track to queue
            if (track.isVideo) {
                checkAndShowVideoResumePrompt(track.id)
            } else {
                _playbackState.update { it.copy(resumePrompt = null) }
            }
            return
        }

        // Cancel previous pending playback job if user clicked another track rapidly
        playJob?.cancel()
        playJob = scope.launch {
            // Windowed queue across IPC to prevent TransactionTooLargeException (1MB Android Binder limit)
            val maxWindowRadius = 25
            val windowStart = kotlin.math.max(0, trackIndex - maxWindowRadius)
            val windowEnd = kotlin.math.min(queue.size, trackIndex + maxWindowRadius + 1)
            val windowedTracks = queue.subList(windowStart, windowEnd)

            // Heavy MediaItem building (including subtitle filesystem queries) offloaded to IO
            val windowedMediaItems = withContext(Dispatchers.IO) {
                windowedTracks.map { qTrack -> buildMediaItem(qTrack) }
            }

            if (!isActive) return@launch

            val windowIndex = trackIndex - windowStart
            player.setMediaItems(windowedMediaItems, windowIndex, 0L)
            player.prepare()
            player.play()

            if (track.isVideo) {
                checkAndShowVideoResumePrompt(track.id)
                val introSeg = withContext(Dispatchers.Default) {
                    OfflineSkipIntroDetector.detectIntroSegment(track)
                }
                _playbackState.update {
                    it.copy(
                        activeSkipIntroSegment = introSeg,
                        isSkipIntroVisible = OfflineSkipIntroDetector.isSkipIntroActive(0L, introSeg)
                    )
                }
            } else {
                _playbackState.update {
                    it.copy(
                        resumePrompt = null,
                        activeSkipIntroSegment = null,
                        isSkipIntroVisible = false,
                        activePrimarySubtitleText = null,
                        activeSecondarySubtitleText = null
                    )
                }
            }
            persistCurrentPlaybackState()
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) {
            persistCurrentVideoPosition()
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = controller ?: return
        fadeInJob?.cancel()
        _playbackState.update { it.copy(isCrossfadeActive = false) }
        player.volume = baseTrackVolume
        player.seekTo(positionMs)

        val introSeg = _playbackState.value.activeSkipIntroSegment
        val isIntroActive = if (introSeg != null) {
            OfflineSkipIntroDetector.isSkipIntroActive(positionMs, introSeg)
        } else false
        val primTrack = _playbackState.value.primarySubtitleTrack
        val secTrack = _playbackState.value.secondarySubtitleTrack
        val primText = primTrack?.cues?.find { positionMs in it.startMs..it.endMs }?.text
        val secText = secTrack?.cues?.find { positionMs in it.startMs..it.endMs }?.text

        _playbackState.update {
            it.copy(
                currentPositionMs = positionMs,
                isSkipIntroVisible = isIntroActive,
                activePrimarySubtitleText = primText,
                activeSecondarySubtitleText = secText
            )
        }
        persistCurrentPlaybackState()
    }

    fun playNext() {
        val player = controller ?: return
        fadeInJob?.cancel()
        _playbackState.update { it.copy(isCrossfadeActive = false) }
        player.volume = baseTrackVolume
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else {
            val nextIdx = _playbackState.value.queueIndex + 1
            if (nextIdx < currentQueue.size) {
                playTrack(currentQueue[nextIdx], currentQueue)
            }
        }
    }

    fun playPrevious() {
        val player = controller ?: return
        fadeInJob?.cancel()
        _playbackState.update { it.copy(isCrossfadeActive = false) }
        player.volume = baseTrackVolume
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            val prevIdx = _playbackState.value.queueIndex - 1
            if (prevIdx >= 0 && prevIdx < currentQueue.size) {
                playTrack(currentQueue[prevIdx], currentQueue)
            } else {
                player.seekTo(0L)
            }
        }
    }

    fun setCrossfadeDuration(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        _playbackState.update { it.copy(crossfadeDurationSec = clamped) }
        settingsRepo?.setPlaybackSettings(clamped, _playbackState.value.gaplessPlayback)
    }

    fun setGaplessPlayback(enabled: Boolean) {
        _playbackState.update { it.copy(gaplessPlayback = enabled) }
        settingsRepo?.setPlaybackSettings(_playbackState.value.crossfadeDurationSec, enabled)
    }

    fun setShuffle(enabled: Boolean) {
        val player = controller ?: return
        player.shuffleModeEnabled = enabled
        _playbackState.update { it.copy(shuffleMode = enabled) }
    }

    fun toggleRepeatMode() {
        val player = controller ?: return
        val nextMode = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }

        val playerRepeatMode = when (nextMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        player.repeatMode = playerRepeatMode
        _playbackState.update { it.copy(repeatMode = nextMode) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val player = controller ?: return
        player.setPlaybackSpeed(speed)
        _playbackState.update { it.copy(playbackSpeed = speed) }
    }

    fun playNext(track: Track) {
        val player = controller ?: return
        val currentList = currentQueue.toMutableList()
        val currentIndex = _playbackState.value.queueIndex

        val mediaItem = buildMediaItem(track)

        val insertIndex = (currentIndex + 1).coerceAtMost(currentList.size)
        currentList.add(insertIndex, track)
        currentQueue = currentList

        player.addMediaItem(insertIndex, mediaItem)
        _playbackState.update { it.copy(queue = currentList) }
        persistCurrentPlaybackState()
    }

    fun addToQueue(track: Track) {
        val player = controller ?: return
        val currentList = currentQueue.toMutableList()

        val mediaItem = buildMediaItem(track)

        currentList.add(track)
        currentQueue = currentList

        player.addMediaItem(mediaItem)
        _playbackState.update { it.copy(queue = currentList) }
        persistCurrentPlaybackState()
    }

    fun removeFromQueue(index: Int) {
        val player = controller ?: return
        if (index < 0 || index >= currentQueue.size) return
        val currentList = currentQueue.toMutableList()
        currentList.removeAt(index)
        currentQueue = currentList
        player.removeMediaItem(index)
        val newIndex = if (index < _playbackState.value.queueIndex) {
            _playbackState.value.queueIndex - 1
        } else {
            _playbackState.value.queueIndex.coerceAtMost(currentList.lastIndex.coerceAtLeast(0))
        }
        _playbackState.update { it.copy(queue = currentList, queueIndex = newIndex) }
        persistCurrentPlaybackState()
    }

    fun clearQueue() {
        val player = controller ?: return
        val current = _playbackState.value.currentTrack
        if (current != null) {
            val currentList = listOf(current)
            currentQueue = currentList
            val currIdx = _playbackState.value.queueIndex
            for (i in player.mediaItemCount - 1 downTo 0) {
                if (i != currIdx) player.removeMediaItem(i)
            }
            _playbackState.update { it.copy(queue = currentList, queueIndex = 0) }
        } else {
            player.clearMediaItems()
            currentQueue = emptyList()
            _playbackState.update { it.copy(queue = emptyList(), queueIndex = 0, currentTrack = null, isPlaying = false) }
        }
        persistCurrentPlaybackState()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val player = controller ?: return
        if (fromIndex < 0 || fromIndex >= currentQueue.size || toIndex < 0 || toIndex >= currentQueue.size || fromIndex == toIndex) return

        val currentList = currentQueue.toMutableList()
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        currentQueue = currentList

        player.moveMediaItem(fromIndex, toIndex)

        val currentTrackId = _playbackState.value.currentTrack?.id
        val newIndex = currentList.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0)
        _playbackState.update { it.copy(queue = currentList, queueIndex = newIndex) }
        persistCurrentPlaybackState()
    }

    fun shuffleQueue() {
        val player = controller ?: return
        val currentTrack = _playbackState.value.currentTrack ?: return
        if (currentQueue.size <= 2) return

        val currentIndex = currentQueue.indexOfFirst { it.id == currentTrack.id }.coerceAtLeast(0)
        val head = currentQueue.subList(0, currentIndex + 1)
        val tail = currentQueue.subList(currentIndex + 1, currentQueue.size).shuffled()
        val newQueue = head + tail
        currentQueue = newQueue

        val remainingCount = player.mediaItemCount - (currentIndex + 1)
        for (i in 0 until remainingCount) {
            player.removeMediaItem(currentIndex + 1)
        }
        for (track in tail) {
            player.addMediaItem(buildMediaItem(track))
        }

        _playbackState.update { it.copy(queue = newQueue) }
        persistCurrentPlaybackState()
    }

    fun seekBy(offsetMs: Long) {
        val player = controller ?: return
        val currentPos = player.currentPosition
        val duration = player.duration.coerceAtLeast(0L)
        val newPos = (currentPos + offsetMs).coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)
        seekTo(newPos)
    }

    fun setVolume(volume: Float) {
        val player = controller ?: return
        player.volume = volume.coerceIn(0f, 1f)
    }

    fun getPlayer(): Player? = GobeatsMediaSessionService.activePlayer ?: controller

    fun toggleVideoMode(enabled: Boolean) {
        _playbackState.update { it.copy(isVideoMode = enabled) }
    }

    fun setVideoAspectMode(mode: VideoAspectMode) {
        _playbackState.update { it.copy(videoAspectMode = mode) }
    }

    fun selectAudioTrack(index: Int) {
        controller?.let { player ->
            val tracks = player.currentTracks
            var count = 0
            for (group in tracks.groups) {
                if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.length) {
                        if (count == index) {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(
                                    androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i)
                                )
                                .build()
                            _playbackState.update { it.copy(selectedAudioTrackIndex = index) }
                            return
                        }
                        count++
                    }
                }
            }
        }
    }

    fun selectSubtitleTrack(index: Int) {
        controller?.let { player ->
            if (index == -1) {
                // Disable subtitles
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                    .build()
                _playbackState.update { it.copy(selectedSubtitleTrackIndex = -1) }
                return
            }

            val tracks = player.currentTracks
            var count = 0
            for (group in tracks.groups) {
                if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                    for (i in 0 until group.length) {
                        if (count == index) {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(
                                    androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i)
                                )
                                .build()
                            _playbackState.update { it.copy(selectedSubtitleTrackIndex = index) }
                            return
                        }
                        count++
                    }
                }
            }
        }
    }

    fun selectVideoQuality(optionId: String) {
        controller?.let { player ->
            val isSingleRendition = _playbackState.value.isSingleRenditionLocalFile
            val paramsBuilder = player.trackSelectionParameters.buildUpon()
            if (isSingleRendition || optionId == "auto" || optionId == "original") {
                // Clear any size constraints so single-rendition local files NEVER go black
                paramsBuilder.clearVideoSizeConstraints()
            } else {
                when (optionId) {
                    "2160p" -> paramsBuilder.setMaxVideoSize(3840, 2160)
                    "1440p" -> paramsBuilder.setMaxVideoSize(2560, 1440)
                    "1080p" -> paramsBuilder.setMaxVideoSize(1920, 1080)
                    "720p" -> paramsBuilder.setMaxVideoSize(1280, 720)
                    "480p" -> paramsBuilder.setMaxVideoSize(854, 480)
                    "360p" -> paramsBuilder.setMaxVideoSize(640, 360)
                }
            }
            player.trackSelectionParameters = paramsBuilder.build()
            _playbackState.update { state ->
                val updated = state.videoQualityOptions.map { it.copy(isSelected = it.id == optionId) }
                state.copy(
                    selectedVideoQualityId = optionId,
                    videoQualityOptions = updated
                )
            }
        }
    }

    fun resumeFromSavedPosition() {
        val prompt = _playbackState.value.resumePrompt ?: return
        controller?.seekTo(prompt.positionMs)
        _playbackState.update {
            it.copy(
                resumePrompt = null,
                currentPositionMs = prompt.positionMs
            )
        }
    }

    fun dismissResumePrompt() {
        _playbackState.update { it.copy(resumePrompt = null) }
    }

    fun addExternalSubtitle(uri: Uri, customLabel: String? = null) {
        val player = controller ?: return

        if (uri.scheme == android.content.ContentResolver.SCHEME_CONTENT) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }

        val detectedSub = SubtitleDetector.createSubtitleConfigurationFromUri(
            context = context,
            uri = uri,
            customLabel = customLabel
        )

        val curPos = player.currentPosition
        val isPlaying = player.isPlaying
        val mediaItemIndex = player.currentMediaItemIndex

        val currentMediaItem = player.currentMediaItem ?: return
        val currentSubConfigs = currentMediaItem.localConfiguration?.subtitleConfigurations ?: emptyList()
        val updatedSubConfigs = currentSubConfigs + detectedSub.toSubtitleConfiguration()

        val updatedMediaItem = currentMediaItem.buildUpon()
            .setSubtitleConfigurations(updatedSubConfigs)
            .build()

        player.replaceMediaItem(mediaItemIndex, updatedMediaItem)
        if (curPos > 0) {
            player.seekTo(curPos)
        }
        if (isPlaying) {
            player.play()
        }

        val updatedList = _playbackState.value.subtitleTracks.toMutableList()
        if (!updatedList.contains(detectedSub.label)) {
            updatedList.add(detectedSub.label)
        }
        val newIndex = updatedList.indexOf(detectedSub.label)
        _playbackState.update {
            it.copy(
                subtitleTracks = updatedList,
                selectedSubtitleTrackIndex = newIndex
            )
        }

        scope.launch {
            delay(350)
            selectSubtitleTrack(newIndex)
        }
    }

    fun setPrimarySubtitleTrack(track: DualSubtitleTrack?) {
        val pos = controller?.currentPosition ?: 0L
        val activeText = track?.cues?.find { pos in it.startMs..it.endMs }?.text
        _playbackState.update {
            it.copy(
                primarySubtitleTrack = track,
                activePrimarySubtitleText = activeText
            )
        }
    }

    fun setSecondarySubtitleTrack(track: DualSubtitleTrack?) {
        val pos = controller?.currentPosition ?: 0L
        val activeText = track?.cues?.find { pos in it.startMs..it.endMs }?.text
        _playbackState.update {
            it.copy(
                secondarySubtitleTrack = track,
                activeSecondarySubtitleText = activeText
            )
        }
    }

    fun loadAiSubtitleTrack(track: Track, languageCode: String, isPrimary: Boolean = true) {
        scope.launch {
            val dualTrack = AiSubtitleEngine.getOrGenerateDualTrack(dbHelper, track, languageCode)
            if (isPrimary) {
                setPrimarySubtitleTrack(dualTrack)
            } else {
                setSecondarySubtitleTrack(dualTrack)
            }
        }
    }

    fun setDialogueBoostMode(mode: com.example.gobeats.core.model.DialogueBoostMode) {
        val app = context.applicationContext as? com.example.gobeats.GobeatsApplication
        app?.dialogueBoostProcessor?.setMode(mode)
        _playbackState.update { it.copy(dialogueBoostMode = mode) }
    }

    fun setAvDelay(delayMs: Long) {
        val app = context.applicationContext as? com.example.gobeats.GobeatsApplication
        val currentProfile = _playbackState.value.currentAudioDeviceProfile
        if (app != null && currentProfile != null) {
            app.audioDeviceProfileManager.setAvDelay(currentProfile.deviceId, delayMs)
        }
        _playbackState.update { it.copy(avDelayMs = delayMs) }
    }

    fun skipIntro() {
        val seg = _playbackState.value.activeSkipIntroSegment ?: return
        val targetMs = OfflineSkipIntroDetector.getSkipTargetMs(seg)
        seekTo(targetMs)
        _playbackState.update { it.copy(isSkipIntroVisible = false) }
    }

    fun setVideoLoudnessBoost(percent: Int) {
        val clamped = percent.coerceIn(100, 200)
        _playbackState.update { it.copy(videoLoudnessBoostPercent = clamped) }
        val gainMb = if (clamped > 100) {
            ((clamped - 100) * 15).coerceIn(0, 1500)
        } else {
            0
        }
        val eqMgr = (context.applicationContext as? com.example.gobeats.GobeatsApplication)?.equalizerManager
        eqMgr?.setVideoLoudnessBoostGainMb(gainMb)
    }

    private var userPreferredSpeed: Float = 1.0f

    fun start2xSpeedPreview() {
        controller?.let { player ->
            if (!_playbackState.value.is2xSpeedPreviewActive) {
                userPreferredSpeed = player.playbackParameters.speed
                player.setPlaybackSpeed(2.0f)
                _playbackState.update { it.copy(is2xSpeedPreviewActive = true, playbackSpeed = 2.0f) }
            }
        }
    }

    fun stop2xSpeedPreview() {
        controller?.let { player ->
            if (_playbackState.value.is2xSpeedPreviewActive) {
                player.setPlaybackSpeed(userPreferredSpeed)
                _playbackState.update { it.copy(is2xSpeedPreviewActive = false, playbackSpeed = userPreferredSpeed) }
            }
        }
    }

    fun setTouchLocked(locked: Boolean) {
        _playbackState.update { it.copy(isTouchLocked = locked) }
    }

    fun setFullscreenCinema(fullscreen: Boolean) {
        _playbackState.update { it.copy(isFullscreenCinema = fullscreen) }
    }

    fun toggleVideoLoop() {
        controller?.let { player ->
            val next = !_playbackState.value.isVideoLoopEnabled
            player.repeatMode = if (next) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            _playbackState.update { it.copy(isVideoLoopEnabled = next) }
        }
    }

    fun toggleStatsForNerds() {
        _playbackState.update { it.copy(showStatsForNerds = !it.showStatsForNerds) }
    }

    fun notifyTrackMetadataChanged(updatedTrack: Track) {
        val currentList = currentQueue.toMutableList()
        val index = currentList.indexOfFirst { it.id == updatedTrack.id }
        if (index != -1) {
            currentList[index] = updatedTrack
            currentQueue = currentList
        }

        val isCurrent = _playbackState.value.currentTrack?.id == updatedTrack.id
        val newCurrentTrack = if (isCurrent) updatedTrack else _playbackState.value.currentTrack

        _playbackState.update {
            it.copy(
                queue = currentList,
                currentTrack = newCurrentTrack
            )
        }

        controller?.let { player ->
            if (index != -1 && index < player.mediaItemCount) {
                player.replaceMediaItem(index, buildMediaItem(updatedTrack))
            }
        }
    }

    fun release() {
        persistCurrentVideoPosition()
        persistCurrentPlaybackState()
        positionJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
    }
}
