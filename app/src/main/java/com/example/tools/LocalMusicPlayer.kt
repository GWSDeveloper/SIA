package com.example.tools

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.provider.MediaStore
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
                "play", "resume" -> if (player != null) {
                    player?.start()
                    ToolExecutionResult(ToolResultStatus.SUCCESS, "Local music playback resumed.")
                } else {
                    loadQueue(query)
                    if (queue.isEmpty()) ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "No matching local music file was found.") else startTrack(0)
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
                    if (queue.isEmpty()) ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "No local music files were found.") else startTrack((currentIndex - 1 + queue.size).mod(queue.size))
                }
                else -> ToolExecutionResult(ToolResultStatus.UNSUPPORTED, "Unsupported local music action.")
            }
        } catch (_: SecurityException) {
            ToolExecutionResult(ToolResultStatus.PERMISSION_REQUIRED, "MUSIC_PERMISSION_REQUIRED: Allow audio/music access in Android permissions.")
        } catch (_: Exception) {
            ToolExecutionResult(ToolResultStatus.PERMANENT_ERROR, "The local music file could not be played.")
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadQueue(query: String) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        val loaded = mutableListOf<Track>()
        context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (cursor.moveToNext()) {
                val title = cursor.getString(titleCol).orEmpty()
                val artist = cursor.getString(artistCol).orEmpty()
                if (query.isBlank() || title.contains(query, true) || artist.contains(query, true)) {
                    loaded.add(Track(ContentUris.withAppendedId(collection, cursor.getLong(idCol)), title, artist))
                }
            }
        }
        queue = loaded
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

    private data class Track(val uri: android.net.Uri, val title: String, val artist: String)
}
