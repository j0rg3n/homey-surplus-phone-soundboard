package com.soundboard.service

import com.soundboard.ErrorCode
import com.soundboard.protocol.MessageProtocol
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

/**
 * Installs the soundboard WebSocket route into a Ktor [Application].
 *
 * @param audioEngine  handles play/stop/stopAll
 * @param registry     tracks active sessions for broadcasting
 * @param deviceName   reported back to clients in hello_ack (defaults to empty string so tests
 *                     don't need Android runtime; PlaybackService passes Build.MODEL)
 */
fun Application.soundboardWebSocketModule(
    audioEngine: AudioEngine,
    registry: SessionRegistry,
    deviceName: String = "",
) {
    install(WebSockets)
    routing {
        webSocket("/") {
            registry.add(this)
            try {
                handleSoundboardSession(audioEngine, deviceName)
            } finally {
                registry.remove(this)
            }
        }
    }
}

internal suspend fun DefaultWebSocketServerSession.handleSoundboardSession(
    audioEngine: AudioEngine,
    deviceName: String,
) {
    // Expect hello as first frame
    val firstFrame = incoming.receive()
    if (firstFrame !is Frame.Text) return

    val firstMsg = try {
        MessageProtocol.deserialize(firstFrame.readText())
    } catch (e: Exception) {
        return
    }

    if (firstMsg !is SoundboardMessage.Hello) {
        send(
            Frame.Text(
                MessageProtocol.serialize(
                    SoundboardMessage.Error(
                        null,
                        ErrorCode.UNKNOWN_MESSAGE,
                        "Expected hello, got ${firstMsg.type}"
                    )
                )
            )
        )
        return
    }

    send(
        Frame.Text(
            MessageProtocol.serialize(
                SoundboardMessage.HelloAck(
                    deviceName = deviceName,
                    version = com.soundboard.Constants.PROTOCOL_VERSION,
                    sounds = emptyList(),
                )
            )
        )
    )

    for (frame in incoming) {
        if (frame !is Frame.Text) continue
        val msg = try {
            MessageProtocol.deserialize(frame.readText())
        } catch (e: Exception) {
            send(
                Frame.Text(
                    MessageProtocol.serialize(
                        SoundboardMessage.Error(
                            null,
                            ErrorCode.UNKNOWN_MESSAGE,
                            e.message ?: "Parse error"
                        )
                    )
                )
            )
            continue
        }

        when (msg) {
            is SoundboardMessage.Play -> {
                audioEngine.play(
                    soundId = msg.soundId,
                    volume = msg.volume,
                    handle = msg.handle,
                    onStarted = {
                        launch {
                            send(
                                Frame.Text(
                                    MessageProtocol.serialize(
                                        SoundboardMessage.Started(
                                            handle = msg.handle,
                                            soundId = msg.soundId,
                                            soundName = msg.soundId,
                                            durationMs = 0,
                                        )
                                    )
                                )
                            )
                        }
                    },
                    onDone = { reason ->
                        launch {
                            send(
                                Frame.Text(
                                    MessageProtocol.serialize(
                                        SoundboardMessage.Done(
                                            handle = msg.handle,
                                            soundName = msg.soundId,
                                            reason = reason,
                                        )
                                    )
                                )
                            )
                        }
                    }
                )
            }
            is SoundboardMessage.Stop -> {
                audioEngine.stop(msg.handle)
            }
            is SoundboardMessage.StopAll -> {
                audioEngine.stopAll()
            }
            is SoundboardMessage.Ping -> {
                send(Frame.Text(MessageProtocol.serialize(SoundboardMessage.Pong)))
            }
            else -> {
                send(
                    Frame.Text(
                        MessageProtocol.serialize(
                            SoundboardMessage.Error(
                                null,
                                ErrorCode.UNKNOWN_MESSAGE,
                                "Unknown type: ${msg.type}"
                            )
                        )
                    )
                )
            }
        }
    }

    audioEngine.fireConnectionLost()
}
