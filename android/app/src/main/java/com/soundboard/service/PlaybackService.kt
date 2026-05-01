package com.soundboard.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.soundboard.Constants
import com.soundboard.ErrorCode
import com.soundboard.protocol.MessageProtocol
import com.soundboard.protocol.SoundboardMessage
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlaybackService : Service() {

    private var server: ApplicationEngine? = null
    private val audioEngine = AudioEngine()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        startWebSocketServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        Log.d(TAG, "Service destroyed")
    }

    private fun startAsForeground() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Soundboard", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Soundboard active")
            .setContentText("Waiting for sounds")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            else
                0
        )
    }

    private fun startWebSocketServer() {
        val port = Constants.DEFAULT_PORT
        Log.d(TAG, "Starting WebSocket server on port $port")

        server = embeddedServer(CIO, port = port) {
            install(WebSockets)
            routing {
                webSocket("/") { handleSession() }
            }
        }.also { it.start(wait = false) }

        Log.d(TAG, "WebSocket server started")
    }

    private suspend fun DefaultWebSocketServerSession.handleSession() {
        Log.d(TAG, "Client connected")

        // Expect hello as first frame
        val firstFrame = incoming.receive()
        if (firstFrame !is Frame.Text) return

        val firstMsg = try {
            MessageProtocol.deserialize(firstFrame.readText())
        } catch (e: Exception) {
            Log.w(TAG, "Bad first frame: ${e.message}")
            return
        }

        if (firstMsg !is SoundboardMessage.Hello) {
            send(Frame.Text(MessageProtocol.serialize(
                SoundboardMessage.Error(null, ErrorCode.UNKNOWN_MESSAGE, "Expected hello, got ${firstMsg.type}")
            )))
            return
        }

        Log.d(TAG, "Received hello (version ${firstMsg.version}), sending hello_ack")
        send(Frame.Text(MessageProtocol.serialize(
            SoundboardMessage.HelloAck(
                deviceName = Build.MODEL,
                version = Constants.PROTOCOL_VERSION,
                sounds = emptyList(),
            )
        )))

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
                    audioEngine.play(
                        soundId = msg.soundId,
                        volume = msg.volume,
                        handle = msg.handle,
                        onStarted = {
                            launch { send(Frame.Text(MessageProtocol.serialize(
                                SoundboardMessage.Started(
                                    handle = msg.handle,
                                    soundId = msg.soundId,
                                    soundName = msg.soundId,
                                    durationMs = 0,
                                )
                            ))) }
                        },
                        onDone = { reason ->
                            launch { send(Frame.Text(MessageProtocol.serialize(
                                SoundboardMessage.Done(
                                    handle = msg.handle,
                                    soundName = msg.soundId,
                                    reason = reason,
                                )
                            ))) }
                        }
                    )
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

        Log.d(TAG, "Client disconnected")
        audioEngine.fireConnectionLost()
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val CHANNEL_ID = "soundboard_service"
        private const val NOTIFICATION_ID = 1
    }
}
