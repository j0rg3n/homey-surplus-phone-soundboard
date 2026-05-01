package com.soundboard

object Constants {
    const val DEFAULT_PORT = 8765
    const val PING_INTERVAL_MS = 15_000L
    const val PING_TIMEOUT_COUNT = 3
    const val RECONNECT_BASE_MS = 1_000L
    const val RECONNECT_MAX_MS = 30_000L
    const val HELLO_TIMEOUT_MS = 5_000L
    const val PROTOCOL_VERSION = "1.0"
}

object Msg {
    const val PLAY = "play"
    const val STOP = "stop"
    const val STOP_ALL = "stop_all"
    const val HELLO = "hello"
    const val HELLO_ACK = "hello_ack"
    const val LIBRARY_UPDATE = "library_update"
    const val STARTED = "started"
    const val DONE = "done"
    const val PING = "ping"
    const val PONG = "pong"
    const val ERROR = "error"
}

object DoneReason {
    const val COMPLETED = "completed"
    const val STOPPED = "stopped"
    const val CONNECTION_LOST = "connection_lost"
}

object ErrorCode {
    const val SOUND_NOT_FOUND = "SOUND_NOT_FOUND"
    const val HANDLE_NOT_FOUND = "HANDLE_NOT_FOUND"
    const val PLAYBACK_FAILED = "PLAYBACK_FAILED"
    const val UNKNOWN_MESSAGE = "UNKNOWN_MESSAGE"
}
