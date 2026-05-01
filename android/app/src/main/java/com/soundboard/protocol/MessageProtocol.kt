package com.soundboard.protocol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.soundboard.Msg

data class SoundInfo(
    val id: String,
    val name: String,
    val durationMs: Long,
    val loop: Boolean,
    val loopStartMs: Long,
    val loopEndMs: Long,
    val defaultVolume: Int,
)

sealed class SoundboardMessage {
    abstract val type: String

    data class Play(val soundId: String, val volume: Int, val handle: String) : SoundboardMessage() {
        override val type = Msg.PLAY
    }
    data class Stop(val handle: String) : SoundboardMessage() {
        override val type = Msg.STOP
    }
    object StopAll : SoundboardMessage() {
        override val type = Msg.STOP_ALL
    }
    data class Hello(val version: String) : SoundboardMessage() {
        override val type = Msg.HELLO
    }
    data class HelloAck(val deviceName: String, val version: String, val sounds: List<SoundInfo>) : SoundboardMessage() {
        override val type = Msg.HELLO_ACK
    }
    data class LibraryUpdate(val sounds: List<SoundInfo>) : SoundboardMessage() {
        override val type = Msg.LIBRARY_UPDATE
    }
    data class Started(val handle: String, val soundId: String, val soundName: String, val durationMs: Long) : SoundboardMessage() {
        override val type = Msg.STARTED
    }
    data class Done(val handle: String, val soundName: String, val reason: String) : SoundboardMessage() {
        override val type = Msg.DONE
    }
    object Ping : SoundboardMessage() {
        override val type = Msg.PING
    }
    object Pong : SoundboardMessage() {
        override val type = Msg.PONG
    }
    data class Error(val handle: String?, val code: String, val message: String) : SoundboardMessage() {
        override val type = Msg.ERROR
    }
}

data class ValidationResult(val valid: Boolean, val errors: List<String>)

private val REQUIRED_FIELDS = mapOf(
    Msg.PLAY to listOf("soundId", "volume", "handle"),
    Msg.STOP to listOf("handle"),
    Msg.STOP_ALL to emptyList(),
    Msg.HELLO to listOf("version"),
    Msg.HELLO_ACK to listOf("deviceName", "version", "sounds"),
    Msg.LIBRARY_UPDATE to listOf("sounds"),
    Msg.STARTED to listOf("handle", "soundId", "soundName", "durationMs"),
    Msg.DONE to listOf("handle", "soundName", "reason"),
    Msg.PING to emptyList(),
    Msg.PONG to emptyList(),
    Msg.ERROR to listOf("code", "message"),
)

object MessageProtocol {

    fun serialize(msg: SoundboardMessage): String = JsonObject().apply {
        addProperty("type", msg.type)
        when (msg) {
            is SoundboardMessage.Play -> {
                addProperty("soundId", msg.soundId)
                addProperty("volume", msg.volume)
                addProperty("handle", msg.handle)
            }
            is SoundboardMessage.Stop -> addProperty("handle", msg.handle)
            is SoundboardMessage.StopAll -> Unit
            is SoundboardMessage.Hello -> addProperty("version", msg.version)
            is SoundboardMessage.HelloAck -> {
                addProperty("deviceName", msg.deviceName)
                addProperty("version", msg.version)
                add("sounds", soundListToJson(msg.sounds))
            }
            is SoundboardMessage.LibraryUpdate -> add("sounds", soundListToJson(msg.sounds))
            is SoundboardMessage.Started -> {
                addProperty("handle", msg.handle)
                addProperty("soundId", msg.soundId)
                addProperty("soundName", msg.soundName)
                addProperty("durationMs", msg.durationMs)
            }
            is SoundboardMessage.Done -> {
                addProperty("handle", msg.handle)
                addProperty("soundName", msg.soundName)
                addProperty("reason", msg.reason)
            }
            is SoundboardMessage.Ping -> Unit
            is SoundboardMessage.Pong -> Unit
            is SoundboardMessage.Error -> {
                if (msg.handle != null) addProperty("handle", msg.handle) else add("handle", com.google.gson.JsonNull.INSTANCE)
                addProperty("code", msg.code)
                addProperty("message", msg.message)
            }
        }
    }.toString()

    fun deserialize(json: String): SoundboardMessage {
        val obj = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON: ${e.message}", e)
        }

        val typeEl = obj.get("type")
        if (typeEl == null || typeEl.isJsonNull || !typeEl.isJsonPrimitive) {
            throw IllegalArgumentException("Message missing required field: type")
        }
        val type = typeEl.asString

        return when (type) {
            Msg.PLAY -> SoundboardMessage.Play(
                soundId = obj.get("soundId").asString,
                volume = obj.get("volume").asInt,
                handle = obj.get("handle").asString,
            )
            Msg.STOP -> SoundboardMessage.Stop(handle = obj.get("handle").asString)
            Msg.STOP_ALL -> SoundboardMessage.StopAll
            Msg.HELLO -> SoundboardMessage.Hello(version = obj.get("version").asString)
            Msg.HELLO_ACK -> SoundboardMessage.HelloAck(
                deviceName = obj.get("deviceName").asString,
                version = obj.get("version").asString,
                sounds = jsonToSoundList(obj.get("sounds").asJsonArray),
            )
            Msg.LIBRARY_UPDATE -> SoundboardMessage.LibraryUpdate(
                sounds = jsonToSoundList(obj.get("sounds").asJsonArray),
            )
            Msg.STARTED -> SoundboardMessage.Started(
                handle = obj.get("handle").asString,
                soundId = obj.get("soundId").asString,
                soundName = obj.get("soundName").asString,
                durationMs = obj.get("durationMs").asLong,
            )
            Msg.DONE -> SoundboardMessage.Done(
                handle = obj.get("handle").asString,
                soundName = obj.get("soundName").asString,
                reason = obj.get("reason").asString,
            )
            Msg.PING -> SoundboardMessage.Ping
            Msg.PONG -> SoundboardMessage.Pong
            Msg.ERROR -> SoundboardMessage.Error(
                handle = obj.get("handle")?.takeIf { !it.isJsonNull }?.asString,
                code = obj.get("code").asString,
                message = obj.get("message").asString,
            )
            else -> throw IllegalArgumentException("Unknown message type: $type")
        }
    }

    fun validate(json: String): ValidationResult {
        val errors = mutableListOf<String>()
        val obj = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            return ValidationResult(valid = false, errors = listOf("Invalid JSON: ${e.message}"))
        }

        val typeEl = obj.get("type")
        if (typeEl == null || typeEl.isJsonNull || !typeEl.isJsonPrimitive) {
            return ValidationResult(valid = false, errors = listOf("Missing required field: type"))
        }
        val type = typeEl.asString

        val required = REQUIRED_FIELDS[type]
            ?: run {
                errors.add("Unknown message type: $type")
                return ValidationResult(valid = false, errors = errors)
            }

        for (field in required) {
            val el = obj.get(field)
            if (el == null || el.isJsonNull) {
                errors.add("Missing required field: $field")
            }
        }
        return ValidationResult(valid = errors.isEmpty(), errors = errors)
    }

    private fun soundListToJson(sounds: List<SoundInfo>): JsonArray = JsonArray().also { arr ->
        sounds.forEach { s ->
            arr.add(JsonObject().apply {
                addProperty("id", s.id)
                addProperty("name", s.name)
                addProperty("durationMs", s.durationMs)
                addProperty("loop", s.loop)
                addProperty("loopStartMs", s.loopStartMs)
                addProperty("loopEndMs", s.loopEndMs)
                addProperty("defaultVolume", s.defaultVolume)
            })
        }
    }

    private fun jsonToSoundList(arr: JsonArray): List<SoundInfo> = arr.map { el ->
        val s = el.asJsonObject
        SoundInfo(
            id = s.get("id").asString,
            name = s.get("name").asString,
            durationMs = s.get("durationMs").asLong,
            loop = s.get("loop").asBoolean,
            loopStartMs = s.get("loopStartMs").asLong,
            loopEndMs = s.get("loopEndMs").asLong,
            defaultVolume = s.get("defaultVolume").asInt,
        )
    }
}
