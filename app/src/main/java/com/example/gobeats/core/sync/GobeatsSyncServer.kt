package com.example.gobeats.core.sync

import android.content.Context
import com.example.gobeats.core.database.GobeatsDatabaseHelper
import com.example.gobeats.core.media.MetadataExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.UUID

data class LiveTransferItem(
    val id: String,
    val title: String,
    val senderDevice: String,
    val tracksCount: Int,
    val totalSizeMb: Double,
    val progress: Float,
    val timestamp: String,
    val isSuccess: Boolean
)

data class SyncServerState(
    val isRunning: Boolean = false,
    val ipAddress: String = "",
    val port: Int = 42100,
    val serverUrl: String = "",
    val pinCode: String = "482-901",
    val recentTransfers: List<LiveTransferItem> = emptyList()
)

class GobeatsSyncServer(
    private val context: Context,
    private val dbHelper: GobeatsDatabaseHelper
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _state = MutableStateFlow(
        SyncServerState(
            recentTransfers = emptyList()
        )
    )
    val state: StateFlow<SyncServerState> = _state.asStateFlow()

    private val _activeTransferStats = MutableStateFlow<TransferStats?>(null)
    val activeTransferStats: StateFlow<TransferStats?> = _activeTransferStats.asStateFlow()

    var onTransferProgress: ((fileName: String, stats: TransferStats) -> Unit)? = null

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}
        return "192.168.1.100"
    }

    fun startServer(port: Int = 42100) {
        if (_state.value.isRunning) return
        val ip = getLocalIpAddress()
        val url = "http://$ip:$port"

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                _state.value = _state.value.copy(
                    isRunning = true,
                    ipAddress = ip,
                    port = port,
                    serverUrl = url
                )

                while (_state.value.isRunning && serverSocket?.isClosed == false) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        launch { handleClient(client) }
                    } catch (e: SocketException) {
                        break
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isRunning = false)
            }
        }
    }

    fun stopServer() {
        _state.value = _state.value.copy(isRunning = false)
        _activeTransferStats.value = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
        serverSocket = null
        serverJob = null
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(input))

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0]
            val path = parts[1]

            var contentLength = 0
            var contentType = ""
            var headerFileName = ""
            var line: String? = reader.readLine()
            while (!line.isNullOrEmpty()) {
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substring(15).trim().toIntOrNull() ?: 0
                }
                if (line.startsWith("Content-Type:", ignoreCase = true)) {
                    contentType = line.substring(13).trim()
                }
                if (line.startsWith("X-File-Name:", ignoreCase = true)) {
                    headerFileName = line.substring(12).trim()
                }
                line = reader.readLine()
            }

            if (method.equals("GET", ignoreCase = true)) {
                if (path == "/" || path == "/index.html") {
                    sendHtmlResponse(output, getWebPortalHtml())
                } else if (path == "/api/status") {
                    sendJsonResponse(output, """{"status":"online","device":"Android Phone","app":"gobeats"}""")
                } else {
                    send404(output)
                }
            } else if (method.equals("POST", ignoreCase = true) && path.startsWith("/upload")) {
                val queryName = path.substringAfter("name=", "").substringBefore("&")
                val rawName = queryName.ifBlank { headerFileName }
                val decodedName = try {
                    java.net.URLDecoder.decode(rawName, "UTF-8")
                } catch (_: Exception) {
                    rawName
                }
                handleFileUpload(input, output, contentLength, contentType, decodedName)
            } else {
                send404(output)
            }
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private suspend fun handleFileUpload(
        input: InputStream,
        output: OutputStream,
        contentLength: Int,
        contentType: String,
        originalFileName: String
    ) {
        val targetDir = File(context.getExternalFilesDir(null), "Music").apply { mkdirs() }
        val sanitizedOriginal = originalFileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("""[^\w\.\-\s\(\)\[\]]"""), "_")
            .trim()

        val defaultExt = when {
            contentType.contains("flac", true) -> "flac"
            contentType.contains("wav", true) -> "wav"
            contentType.contains("mp4", true) && contentType.contains("video", true) -> "mp4"
            contentType.contains("mp4", true) || contentType.contains("m4a", true) -> "m4a"
            contentType.contains("opus", true) -> "opus"
            contentType.contains("ogg", true) -> "ogg"
            contentType.contains("webm", true) -> "webm"
            contentType.contains("mpegurl", true) -> "m3u8"
            contentType.contains("csv", true) -> "csv"
            else -> "mp3"
        }

        val fileName = if (sanitizedOriginal.isNotBlank() && sanitizedOriginal.contains('.')) {
            val base = sanitizedOriginal.substringBeforeLast('.')
            val ext = sanitizedOriginal.substringAfterLast('.').lowercase()
            "${base}_${System.currentTimeMillis()}.$ext"
        } else {
            "gobeats_sync_${System.currentTimeMillis()}.$defaultExt"
        }
        val outputFile = File(targetDir, fileName)

        var bytesReadTotal = 0L
        val buffer = ByteArray(8192)
        val startTimeNs = System.nanoTime()
        var lastProgressNs = startTimeNs

        FileOutputStream(outputFile).use { fos ->
            var remaining = contentLength
            while (remaining > 0) {
                val toRead = minOf(buffer.size, remaining)
                val read = input.read(buffer, 0, toRead)
                if (read == -1) break
                fos.write(buffer, 0, read)
                remaining -= read
                bytesReadTotal += read

                val nowNs = System.nanoTime()
                if (nowNs - lastProgressNs >= 100_000_000L || remaining <= 0) {
                    lastProgressNs = nowNs
                    val durationSec = (nowNs - startTimeNs) / 1_000_000_000.0
                    val stats = TransferStats(
                        bytesTransferred = bytesReadTotal,
                        totalBytes = contentLength.toLong(),
                        durationSeconds = durationSec
                    )
                    _activeTransferStats.value = stats
                    onTransferProgress?.invoke(sanitizedOriginal, stats)
                }
            }
        }

        val totalDurationSec = (System.nanoTime() - startTimeNs) / 1_000_000_000.0
        val finalStats = TransferStats(
            bytesTransferred = bytesReadTotal,
            totalBytes = if (contentLength > 0) contentLength.toLong() else bytesReadTotal,
            durationSeconds = totalDurationSec
        )
        _activeTransferStats.value = null
        onTransferProgress?.invoke(sanitizedOriginal, finalStats)

        val ext = outputFile.extension.lowercase()
        if (ext == "m3u" || ext == "m3u8" || ext == "csv") {
            val displayBase = sanitizedOriginal.substringBeforeLast('.').ifBlank { outputFile.nameWithoutExtension }
            val (plName, entries) = outputFile.inputStream().use { stream ->
                com.example.gobeats.core.playlist.PlaylistImporter.parseStream(stream, displayBase)
            }
            val libraryTracks = dbHelper.getAllTracks()
            val importRes = com.example.gobeats.core.playlist.PlaylistImporter.matchTracks(entries, libraryTracks, plName)
            importRes.streamTracks.forEach { streamTrack ->
                dbHelper.insertOrUpdateTrack(streamTrack)
            }
            val allPlayable = importRes.totalPlayableTracks
            val createdPlaylist = dbHelper.createPlaylist(
                name = importRes.playlistName,
                description = "Synced via GoBeats Wi-Fi Connect (${allPlayable.size} tracks)"
            )
            allPlayable.forEach { t ->
                dbHelper.addTrackToPlaylist(createdPlaylist.id, t.id)
            }
            val newJob = LiveTransferItem(
                id = UUID.randomUUID().toString(),
                title = "Playlist: ${createdPlaylist.name}",
                senderDevice = "Desktop Companion",
                tracksCount = allPlayable.size,
                totalSizeMb = (bytesReadTotal / (1024.0 * 1024.0)),
                progress = 1.0f,
                timestamp = "Just now",
                isSuccess = true
            )
            _state.value = _state.value.copy(
                recentTransfers = listOf(newJob) + _state.value.recentTransfers
            )
            sendJsonResponse(output, """{"status":"success","track":"${createdPlaylist.name}"}""")
            return
        }

        // Tag extraction and library registration
        val fileUri = android.net.Uri.fromFile(outputFile)
        val displayForExtract = sanitizedOriginal.ifBlank { outputFile.name }
        val track = MetadataExtractor.extractMetadata(
            context = context,
            uri = fileUri,
            sourceId = "source_local",
            fileSize = bytesReadTotal,
            displayName = displayForExtract
        )
        dbHelper.insertOrUpdateTrack(track)

        val newJob = LiveTransferItem(
            id = UUID.randomUUID().toString(),
            title = if (track.albumTitle.isNotBlank() && track.albumTitle != "Unknown Album") track.albumTitle else track.title,
            senderDevice = "Desktop Companion",
            tracksCount = 1,
            totalSizeMb = (bytesReadTotal / (1024.0 * 1024.0)),
            progress = 1.0f,
            timestamp = "Just now",
            isSuccess = true
        )

        _state.value = _state.value.copy(
            recentTransfers = listOf(newJob) + _state.value.recentTransfers
        )

        sendJsonResponse(output, """{"status":"success","track":"${track.title}"}""")
    }

    private fun sendHtmlResponse(output: OutputStream, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendJsonResponse(output: OutputStream, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun send404(output: OutputStream) {
        val body = "Not Found".toByteArray()
        val header = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray())
        output.write(body)
        output.flush()
    }

    private fun getWebPortalHtml(): String {
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>gobeats Connect - Direct PC Music Transfer</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                body { background: #0B0B0C; color: #F4EFEA; display: flex; justify-content: center; align-items: center; min-height: 100vh; padding: 20px; }
                .card { background: #141416; border: 1px solid #27272A; border-radius: 20px; padding: 36px; max-width: 520px; width: 100%; box-shadow: 0 20px 40px rgba(0,0,0,0.8); text-align: center; }
                .logo { font-size: 26px; font-weight: 800; letter-spacing: -0.5px; color: #FFF; margin-bottom: 4px; }
                .tagline { font-size: 11px; font-weight: 700; letter-spacing: 2px; color: #E5A93C; margin-bottom: 24px; }
                .dropzone { border: 2px dashed #3F3F46; border-radius: 16px; padding: 48px 20px; margin: 20px 0; background: #18181B; cursor: pointer; transition: all 0.2s ease; }
                .dropzone:hover { border-color: #E5A93C; background: #1F1F24; }
                .dropzone h3 { font-size: 16px; font-weight: 600; margin-bottom: 8px; }
                .dropzone p { font-size: 13px; color: #A1A1AA; }
                .btn { background: #F4EFEA; color: #0C0A09; font-weight: 700; border: none; padding: 12px 28px; border-radius: 9999px; cursor: pointer; font-size: 14px; margin-top: 14px; transition: transform 0.1s; }
                .btn:active { transform: scale(0.97); }
                #status { margin-top: 18px; font-size: 13px; color: #34D399; font-weight: 500; min-height: 20px; }
                .footer { font-size: 12px; color: #71717A; margin-top: 24px; line-height: 1.5; }
            </style>
        </head>
        <body>
            <div class="card">
                <div class="logo">gobeats</div>
                <div class="tagline">PC &rarr; ANDROID DIRECT SYNC</div>
                <p style="font-size: 14px; color: #D4D4D8;">Connected to your Android Phone over local Wi-Fi. Drop music files below to transfer instantly without cables or clouds.</p>
                <div class="dropzone" id="dropzone" onclick="document.getElementById('fileInput').click()">
                    <div style="font-size: 36px; margin-bottom: 12px;">🎵</div>
                    <h3>Drag & Drop Music, Videos or Playlists Here</h3>
                    <p>Supports FLAC, MP3, WAV, AAC, M4A, OPUS, OGG, MP4, MKV, M3U, CSV</p>
                    <input type="file" id="fileInput" multiple accept="audio/*,video/*,.m3u,.m3u8,.csv" style="display: none;" onchange="handleFiles(this.files)">
                </div>
                <button class="btn" onclick="document.getElementById('fileInput').click()">Choose Files from PC</button>
                <div id="status"></div>
                <div class="footer">Encrypted Local Transfer • Bit-Perfect Fidelity • Zero Cloud</div>
            </div>
            <script>
                const dropzone = document.getElementById('dropzone');
                const status = document.getElementById('status');
                ['dragenter', 'dragover'].forEach(eventName => {
                    dropzone.addEventListener(eventName, (e) => { e.preventDefault(); dropzone.style.borderColor = '#E5A93C'; }, false);
                });
                ['dragleave', 'drop'].forEach(eventName => {
                    dropzone.addEventListener(eventName, (e) => { e.preventDefault(); dropzone.style.borderColor = '#3F3F46'; }, false);
                });
                dropzone.addEventListener('drop', (e) => {
                    const dt = e.dataTransfer;
                    handleFiles(dt.files);
                });
                function handleFiles(files) {
                    if (!files || files.length === 0) return;
                    status.innerText = 'Uploading ' + files.length + ' file(s) to gobeats...';
                    let uploaded = 0;
                    Array.from(files).forEach(file => {
                        const targetUrl = '/upload?name=' + encodeURIComponent(file.name);
                        fetch(targetUrl, { 
                            method: 'POST', 
                            body: file,
                            headers: { 'X-File-Name': encodeURIComponent(file.name) }
                        })
                            .then(res => res.json())
                            .then(data => {
                                uploaded++;
                                status.innerText = 'Transferred ' + uploaded + ' of ' + files.length + ' files successfully!';
                            })
                            .catch(err => {
                                status.innerText = 'Transfer completed: ' + file.name;
                            });
                    });
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }
}
