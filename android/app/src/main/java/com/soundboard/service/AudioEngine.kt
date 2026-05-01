package com.soundboard.service

import android.media.MediaPlayer
import android.util.Log
import com.soundboard.DoneReason
import kotlin.math.pow

private const val TAG = "AudioEngine"

fun mapVolume(volume: Int): Float =
    (volume / 100.0).pow(1.5).toFloat().coerceAtMost(1.0f)

internal interface Player {
    fun prepare(filePath: String, volume: Float, loop: Boolean)
    fun start()
    fun stop()
    fun release()
    fun setOnCompletionListener(cb: () -> Unit)
    fun setOnErrorListener(cb: (Int, Int) -> Boolean)
}

private class MediaPlayerAdapter : Player {
    private val mp = MediaPlayer()

    override fun prepare(filePath: String, volume: Float, loop: Boolean) {
        mp.setDataSource(filePath)
        mp.setVolume(volume, volume)
        mp.isLooping = loop
        mp.prepare()
    }
    override fun start() = mp.start()
    override fun stop() = mp.stop()
    override fun release() = mp.release()
    override fun setOnCompletionListener(cb: () -> Unit) { mp.setOnCompletionListener { cb() } }
    override fun setOnErrorListener(cb: (Int, Int) -> Boolean) {
        mp.setOnErrorListener { _, what, extra -> cb(what, extra) }
    }
}

class AudioEngine(
    private val playerFactory: () -> Player = { MediaPlayerAdapter() }
) {
    private data class ActiveSound(
        val player: Player,
        val onDone: (String) -> Unit,
    )

    private val active = mutableMapOf<String, ActiveSound>()

    fun play(
        filePath: String,
        soundName: String,
        volume: Int,
        loop: Boolean,
        handle: String,
        onStarted: () -> Unit,
        onDone: (reason: String) -> Unit,
    ): Boolean {
        val gain = mapVolume(volume)
        val player = try {
            playerFactory().also { it.prepare(filePath, gain, loop) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare $soundName: ${e.message}")
            return false
        }

        player.setOnCompletionListener {
            active.remove(handle)
            player.release()
            onDone(DoneReason.COMPLETED)
        }
        player.setOnErrorListener { what, extra ->
            Log.e(TAG, "MediaPlayer error for $soundName: what=$what extra=$extra")
            active.remove(handle)
            player.release()
            onDone(DoneReason.COMPLETED)
            true
        }

        active[handle] = ActiveSound(player, onDone)
        player.start()
        onStarted()
        return true
    }

    fun stop(handle: String) {
        active.remove(handle)?.let { sound ->
            sound.player.stop()
            sound.player.release()
            sound.onDone(DoneReason.STOPPED)
        }
    }

    fun stopAll() {
        val snapshot = active.values.toList()
        active.clear()
        snapshot.forEach { sound ->
            sound.player.stop()
            sound.player.release()
            sound.onDone(DoneReason.STOPPED)
        }
    }

    fun fireConnectionLost() {
        val snapshot = active.values.toList()
        active.clear()
        snapshot.forEach { sound ->
            sound.player.stop()
            sound.player.release()
            sound.onDone(DoneReason.CONNECTION_LOST)
        }
    }

    fun activeHandles(): List<String> = active.keys.toList()
}
