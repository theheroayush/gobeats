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
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.RepeatMode
import com.example.gobeats.core.model.Track
import com.example.gobeats.core.model.VideoAspectMode
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    private var pendingPlay: Pair<Track, List<Track>>? = null

    init {
        initController()
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
            pendingPlay?.let { (t, q) ->
                pendingPlay = null
                playTrack(t, q)
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
                if (isPlaying) startPositionUpdates() else stopPositionUpdates()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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

                if (currentTrack != null) {
                    scope.launch(Dispatchers.IO) {
                        dbHelper.recordTrackPlayed(currentTrack.id)
                        val eqMgr = (context.applicationContext as? com.example.gobeats.GobeatsApplication)?.equalizerManager
                        if (eqMgr != null) {
                            val rgResult = eqMgr.computeTrackReplayGain(currentTrack)
                            scope.launch(Dispatchers.Main) {
                                controller?.volume = rgResult.linearVolume
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
                        isSelected = currentSelectedQuality == "auto",
                        isAuto = true
                    )
                )

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

                _playbackState.update {
                    it.copy(
                        audioTracks = audioList,
                        selectedAudioTrackIndex = selectedAudioIdx,
                        subtitleTracks = subtitleList,
                        selectedSubtitleTrackIndex = selectedSubtitleIdx,
                        videoQualityOptions = qualityList
                    )
                }
            }
        })
    }

    var onTrackEndedListener: (() -> Unit)? = null

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                controller?.let { player ->
                    var liveStats: com.example.gobeats.core.model.VideoPlaybackStats? = null
                    if (_playbackState.value.isVideoMode) {
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

                    _playbackState.update {
                        it.copy(
                            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                            durationMs = player.duration.coerceAtLeast(0L),
                            videoStats = liveStats ?: it.videoStats
                        )
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
    }

    fun playTrack(track: Track, queue: List<Track>) {
        val player = controller
        val trackIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        if (player == null) {
            pendingPlay = track to queue
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
            return
        }

        currentQueue = queue

        val mediaItems = queue.map { qTrack ->
            val metadata = MediaMetadata.Builder()
                .setTitle(qTrack.title)
                .setArtist(qTrack.artistName)
                .setAlbumTitle(qTrack.albumTitle)
                .setArtworkUri(if (qTrack.artworkUri != null) Uri.parse(qTrack.artworkUri) else null)
                .build()

            MediaItem.Builder()
                .setMediaId(qTrack.id)
                .setUri(Uri.parse(qTrack.fileUri))
                .setMediaMetadata(metadata)
                .build()
        }

        player.setMediaItems(mediaItems, trackIndex, 0L)
        player.prepare()
        player.play()

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
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = controller ?: return
        player.seekTo(positionMs)
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun playNext() {
        val player = controller ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    fun playPrevious() {
        val player = controller ?: return
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0L)
        }
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

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistName)
            .setAlbumTitle(track.albumTitle)
            .setArtworkUri(if (track.artworkUri != null) Uri.parse(track.artworkUri) else null)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(track.fileUri))
            .setMediaMetadata(metadata)
            .build()

        val insertIndex = (currentIndex + 1).coerceAtMost(currentList.size)
        currentList.add(insertIndex, track)
        currentQueue = currentList

        player.addMediaItem(insertIndex, mediaItem)
        _playbackState.update { it.copy(queue = currentList) }
    }

    fun addToQueue(track: Track) {
        val player = controller ?: return
        val currentList = currentQueue.toMutableList()

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artistName)
            .setAlbumTitle(track.albumTitle)
            .setArtworkUri(if (track.artworkUri != null) Uri.parse(track.artworkUri) else null)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(track.fileUri))
            .setMediaMetadata(metadata)
            .build()

        currentList.add(track)
        currentQueue = currentList

        player.addMediaItem(mediaItem)
        _playbackState.update { it.copy(queue = currentList) }
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
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artistName)
                .setAlbumTitle(track.albumTitle)
                .setArtworkUri(if (track.artworkUri != null) Uri.parse(track.artworkUri) else null)
                .build()
            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(Uri.parse(track.fileUri))
                .setMediaMetadata(metadata)
                .build()
            player.addMediaItem(mediaItem)
        }

        _playbackState.update { it.copy(queue = newQueue) }
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

    fun getPlayer(): Player? = controller

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
            val paramsBuilder = player.trackSelectionParameters.buildUpon()
            when (optionId) {
                "auto" -> paramsBuilder.clearVideoSizeConstraints()
                "2160p" -> paramsBuilder.setMaxVideoSize(3840, 2160)
                "1440p" -> paramsBuilder.setMaxVideoSize(2560, 1440)
                "1080p" -> paramsBuilder.setMaxVideoSize(1920, 1080)
                "720p" -> paramsBuilder.setMaxVideoSize(1280, 720)
                "480p" -> paramsBuilder.setMaxVideoSize(854, 480)
                "360p" -> paramsBuilder.setMaxVideoSize(640, 360)
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
                val metadata = MediaMetadata.Builder()
                    .setTitle(updatedTrack.title)
                    .setArtist(updatedTrack.artistName)
                    .setAlbumTitle(updatedTrack.albumTitle)
                    .setArtworkUri(if (updatedTrack.artworkUri != null) Uri.parse(updatedTrack.artworkUri) else null)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setMediaId(updatedTrack.id)
                    .setUri(Uri.parse(updatedTrack.fileUri))
                    .setMediaMetadata(metadata)
                    .build()

                player.replaceMediaItem(index, mediaItem)
            }
        }
    }

    fun release() {
        positionJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
    }
}
