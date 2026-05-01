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
import com.soundboard.protocol.MessageProtocol
import com.soundboard.protocol.SoundInfo
import com.soundboard.protocol.SoundboardMessage
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlaybackService : Service() {

    private var server: ApplicationEngine? = null
    private val audioEngine = AudioEngine()
    val sessionRegistry = SessionRegistry()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    suspend fun broadcastLibraryUpdate(sounds: List<SoundInfo>) {
        val frame = Frame.Text(
            MessageProtocol.serialize(SoundboardMessage.LibraryUpdate(sounds))
        )
        sessionRegistry.all().forEach { session ->
            try {
                session.send(frame)
            } catch (_: Exception) {
            }
        }
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
            soundboardWebSocketModule(audioEngine, sessionRegistry, Build.MODEL)
        }.also { it.start(wait = false) }

        Log.d(TAG, "WebSocket server started")
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val CHANNEL_ID = "soundboard_service"
        private const val NOTIFICATION_ID = 1
    }
}
