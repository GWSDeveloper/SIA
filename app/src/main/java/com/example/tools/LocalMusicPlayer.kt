package com.example.tools

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.provider.MediaStore
import android.icu.text.Transliterator
import java.text.Normalizer
import java.util.Locale
import androidx.core.content.ContextCompat

/** Minimal local-file player. Online streaming is deliberately not part of this version. */
class LocalMusicPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var queue: List<Track> = emptyList()
    private var currentIndex = -1

    @Synchronized
    fun control(action: String, query: String = ""): ToolExecutionResult {
        if (!hasAudioPermission()) return ToolExecutionResult(ToolResultStatus.PERMISSION_REQUIRED, "MUSIC_PERMISSION_REQUIRED: Allow audio/music access in Android permissions to play local songs.")
        return try {
            when (action.lowercase()) {
                "play", "resume" -> {
                    // A named track request must always resolve that track, even if another
                    // SIA track is already loaded. Never treat "play this song" as resume.
                    if (query.isNotBlank()) {
                        loadQueue(query)
                        if (queue.isEmpty()) {
                            ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "No local song matched '$query'. Check the title or artist in your music library.")
                        } else startTrack(0)
                    } else if (player != null) {
                        player?.start()
                        ToolExecutionResult(ToolResultStatus.SUCCESS, "Local music playback resumed.")
                    } else {
                        loadQueue("")
                        if (queue.isEmpty()) ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "No local music files were found.") else startTrack(0)
                    }
                }
                "pause" -> {
                    if (player == null) ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "No SIA local track is currently playing.")
                    else {
                        if (player?.isPlaying == true) player?.pause()
                        ToolExecutionResult(ToolResultStatus.SUCCESS, "Local music paused.")
                    }
                }
                "stop" -> {
                    if (player == null) ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "No SIA local track is currently playing.")
                    else {
                        releasePlayer()
                        ToolExecutionResult(ToolResultStatus.SUCCESS, "Local music stopped.")
                    }
                }
                "next" -> {
                    if (queue.isEmpty()) loadQueue("")
                    if (queue.isEmpty()) ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "No local music files were found.") else startTrack((currentIndex + 1).mod(queue.size))
                }
                "previous", "prev" -> {
                    if (queue.isEmpty()) loadQueue("")
                    if (queue.isEmpty()) ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "No local music files were found.") else startTrack(if (currentIndex < 0) queue.lastIndex else (currentIndex - 1 + queue.size).mod(queue.size))
                }
                else -> ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "Unsupported local music action.")
            }
        } catch (_: SecurityException) {
            ToolExecutionResult(ToolResultStatus.PERMISSION_REQUIRED, "MUSIC_PERMISSION_REQUIRED: Allow audio/music access in Android permissions.")
        } catch (_: Exception) {
            ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "The local music file could not be played.")
        }
    }

    @Synchronized
    fun listTracks(query: String = ""): ToolExecutionResult {
        if (!hasAudioPermission()) {
            return ToolExecutionResult(
                ToolResultStatus.PERMISSION_REQUIRED,
                "MUSIC_PERMISSION_REQUIRED: Allow audio/music access to list local tracks."
            )
        }
        val originalQueue = queue
        val originalIndex = currentIndex
        return try {
            loadQueue(query)
            val matches = queue.toList()
            queue = originalQueue
            currentIndex = originalIndex
            if (matches.isEmpty()) {
                ToolExecutionResult(
                    ToolResultStatus.SUCCESS,
                    if (query.isBlank()) "No local music files were found." else "No local tracks matched '$query'.",
                    data = mapOf("count" to "0")
                )
            } else {
                val listed = matches.take(25).joinToString("; ") { track ->
                    if (track.artist.isBlank() || track.artist.equals("<unknown>", true)) track.title
                    else "${track.title} by ${track.artist}"
                }
                ToolExecutionResult(
                    ToolResultStatus.SUCCESS,
                    "Found ${matches.size} local track(s). Showing up to 25: $listed",
                    data = mapOf("count" to matches.size.toString())
                )
            }
        } catch (_: SecurityException) {
            queue = originalQueue
            currentIndex = originalIndex
            ToolExecutionResult(ToolResultStatus.PERMISSION_REQUIRED, "MUSIC_PERMISSION_REQUIRED: Allow audio/music access to list local tracks.")
        } catch (_: Exception) {
            queue = originalQueue
            currentIndex = originalIndex
            ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "Could not list local music files.")
        }
    }

    @Synchronized
    fun currentTrack(): ToolExecutionResult {
        val active = player
            ?: return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "SIA's local player has no current track.")
        return try {
            if (!active.isPlaying) {
                ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "SIA's local player is not currently playing.")
            } else {
                val track = queue.getOrNull(currentIndex)
                    ?: return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "SIA could not identify its current local track.")
                ToolExecutionResult(
                    ToolResultStatus.SUCCESS,
                    "Current SIA local track: ${track.title} by ${track.artist}."
                )
            }
        } catch (_: Exception) {
            ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "SIA could not verify the current local track.")
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadQueue(query: String) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        val allTracks = mutableListOf<Track>()
        context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) {
                val title = cursor.getString(titleCol).orEmpty()
                val artist = cursor.getString(artistCol).orEmpty()
                allTracks.add(Track(ContentUris.withAppendedId(collection, cursor.getLong(idCol)), title, artist))
            }
        }
        if (query.isBlank()) {
            queue = allTracks
            return
        }
        // Remove command filler before matching, then require a real title/artist match.
        // Loose token overlap was allowing unrelated tracks to win (e.g. Humdum -> Saiyaara).
        val queryNorm = normalized(query)
            .split(" ")
            .filterNot { it in setOf("play", "please", "song", "gaana", "gana", "wala", "wali", "waala", "waali", "kar", "karo", "do", "baja", "bajao", "chala", "chalao", "mere", "phone", "mein", "me") }
            .joinToString(" ")
            .trim()
        if (queryNorm.isBlank()) {
            queue = emptyList()
            return
        }
        val queryLatin = transliterate(queryNorm)
        queue = allTracks.mapNotNull { track ->
            val title = normalized(track.title)
            val artist = normalized(track.artist)
            val titleLatin = transliterate(title)
            val artistLatin = transliterate(artist)
            val score = when {
                title == queryNorm || titleLatin == queryLatin -> 100
                title.contains(queryNorm) || titleLatin.contains(queryLatin) -> 95
                queryNorm.contains(title) && title.length >= 3 -> 92
                queryLatin.contains(titleLatin) && titleLatin.length >= 3 -> 90
                artist == queryNorm || artistLatin == queryLatin -> 85
                artist.contains(queryNorm) || artistLatin.contains(queryLatin) -> 80
                else -> 0
            }
            if (score == 0) null else track.copy(matchScore = score)
        }.sortedByDescending { it.matchScore }
    }

    private fun normalized(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun transliterate(value: String): String = try {
        Transliterator.getInstance("Devanagari-Latin; Latin-ASCII").transliterate(value).lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")
    } catch (_: Exception) { value }

    private fun tokenOverlap(query: String, candidate: String): Double {
        val tokens = query.split(" ").filter { it.length > 1 }.toSet()
        if (tokens.isEmpty()) return 0.0
        return tokens.count { token -> candidate.split(" ").any { it.contains(token) || token.contains(it) } }.toDouble() / tokens.size
    }

    private fun startTrack(index: Int): ToolExecutionResult {
        if (queue.isEmpty()) return ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "No local music file was found.")
        currentIndex = index.coerceIn(0, queue.lastIndex)
        releasePlayer()
        val track = queue[currentIndex]
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setDataSource(context, track.uri)
            setOnCompletionListener { control("next") }
            prepare()
            start()
        }
        return ToolExecutionResult(ToolResultStatus.SUCCESS, "Now playing local track: ${track.title} by ${track.artist}.")
    }

    private fun releasePlayer() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    private data class Track(val uri: android.net.Uri, val title: String, val artist: String, val matchScore: Int = 0)
}
