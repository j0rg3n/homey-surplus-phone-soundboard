package com.soundboard.service

import android.util.Log
import com.soundboard.ErrorCode
import com.soundboard.data.ActivePlayback
import com.soundboard.data.PlaybackRepository
import com.soundboard.data.SampleEntity
import com.soundboard.protocol.MessageProtocol
import com.soundboard.protocol.SoundInfo
import com.soundboard.protocol.SoundboardMessage
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.launch

private const val TAG = "PlaybackService"

class SessionRegistry {
    private val sessions =
        java.util.concurrent.CopyOnWriteArrayList<DefaultWebSocketServerSession>()

    fun add(s: DefaultWebSocketServerSession) {
        sessions.add(s)
    }

    fun remove(s: DefaultWebSocketServerSession) {
        sessions.remove(s)
    }

    fun all(): List<DefaultWebSocketServerSession> = sessions.toList()
}

fun Application.soundboardWebSocketModule(
    audioEngine: AudioEngine,
    registry: SessionRegistry,
    deviceName: String = "",
    sounds: () -> List<SoundInfo> = { emptyList() },
    lookupSound: suspend (soundId: String) -> SampleEntity? = { null },
    playbackRepository: PlaybackRepository? = null,
) {
    install(WebSockets)
    routing {
        webSocket("/") {
            registry.add(this)
            try {
                handleSoundboardSession(audioEngine, deviceName, sounds(), lookupSound, playbackRepository)
            } finally {
                registry.remove(this)
            }
        }
    }
}

internal suspend fun DefaultWebSocketServerSession.handleSoundboardSession(
    audioEngine: AudioEngine,
    deviceName: String,
    sounds: List<SoundInfo> = emptyList(),
    lookupSound: suspend (soundId: String) -> SampleEntity? = { null },
    playbackRepository: PlaybackRepository? = null,
) {
    Log.d(TAG, "Client connected")
    val firstFrame = incoming.receive()
    if (firstFrame !is Frame.Text) return

    val firstMsg = try {
        MessageProtocol.deserialize(firstFrame.readText())
    } catch (e: Exception) {
        return
    }

    if (firstMsg !is SoundboardMessage.Hello) {
        send(Frame.Text(MessageProtocol.serialize(
            SoundboardMessage.Error(null, ErrorCode.UNKNOWN_MESSAGE, "Expected hello, got ${firstMsg.type}")
        )))
        return
    }

    send(Frame.Text(MessageProtocol.serialize(
        SoundboardMessage.HelloAck(
            deviceName = deviceName,
            version = com.soundboard.Constants.PROTOCOL_VERSION,
            sounds = sounds,
        )
    )))

    try {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            val msg = try {
                MessageProtocol.deserialize(frame.readText())
            } catch (e: Exception) {
                send(Frame.Text(MessageProtocol.serialize(
                    SoundboardMessage.Error(null, ErrorCode.UNKNOWN_MESSAGE, e.message ?: "Parse error")
                )))
                continue
            }

            when (msg) {
                is SoundboardMessage.Play -> {
                    Log.d(TAG, "PLAY soundId=${msg.soundId} volume=${msg.volume} handle=${msg.handle}")
                    val sample = lookupSound(msg.soundId)
                    if (sample == null) {
                        send(Frame.Text(MessageProtocol.serialize(
                            SoundboardMessage.Error(msg.handle, ErrorCode.SOUND_NOT_FOUND, "Sound not found: ${msg.soundId}")
                        )))
                        continue
                    }
                    val ok = audioEngine.play(
                        filePath = sample.filePath,
                        soundName = sample.name,
                        volume = msg.volume,
                        loop = sample.loop,
                        handle = msg.handle,
                        onStarted = {
                            launch {
                                send(Frame.Text(MessageProtocol.serialize(
                                    SoundboardMessage.Started(
                                        handle = msg.handle,
                                        soundId = msg.soundId,
                                        soundName = sample.name,
                                        durationMs = sample.durationMs,
                                    )
                                )))
                                playbackRepository?.add(ActivePlayback(
                                    handle = msg.handle,
                                    sampleId = msg.soundId,
                                    sampleName = sample.name,
                                    startedAt = System.currentTimeMillis(),
                                    durationMs = sample.durationMs,
                                ))
                            }
                        },
                        onDone = { reason ->
                            launch {
                                runCatching {
                                    send(Frame.Text(MessageProtocol.serialize(
                                        SoundboardMessage.Done(
                                            handle = msg.handle,
                                            soundName = sample.name,
                                            reason = reason,
                                        )
                                    )))
                                }
                                playbackRepository?.remove(msg.handle)
                            }
                        },
                    )
                    if (!ok) {
                        send(Frame.Text(MessageProtocol.serialize(
                            SoundboardMessage.Error(msg.handle, ErrorCode.PLAYBACK_FAILED, "Failed to play: ${sample.name}")
                        )))
                    }
                }
                is SoundboardMessage.Stop -> {
                    Log.d(TAG, "STOP handle=${msg.handle}")
                    audioEngine.stop(msg.handle)
                }
                is SoundboardMessage.StopAll -> {
                    Log.d(TAG, "STOP_ALL")
                    audioEngine.stopAll()
                }
                is SoundboardMessage.Ping -> {
                    send(Frame.Text(MessageProtocol.serialize(SoundboardMessage.Pong)))
                }
                else -> {
                    send(Frame.Text(MessageProtocol.serialize(
                        SoundboardMessage.Error(null, ErrorCode.UNKNOWN_MESSAGE, "Unknown type: ${msg.type}")
                    )))
                }
            }
        }
    } finally {
        Log.d(TAG, "Client disconnected")
        audioEngine.fireConnectionLost()
        playbackRepository?.clear()
    }
}
