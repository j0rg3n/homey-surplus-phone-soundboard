'use strict';

const { Device } = require('homey');
const { randomUUID } = require('crypto');
const WebSocketClient = require('../../lib/WebSocketClient');
const PlaybackHandleStore = require('../../lib/PlaybackHandleStore');
const { MSG, PROTOCOL_VERSION } = require('../../lib/constants');

class SoundboardDevice extends Device {

  async onInit() {
    const { ip, port } = this.getStore();
    this.log(`Initialising device — ${ip}:${port}`);
    this._handles = new PlaybackHandleStore();

    this._client = this._createClient({
      ip,
      port,
      onMessage: (msg) => this._handleMessage(msg),
      onConnect: (helloAck) => {
        this.log('Connected:', helloAck.deviceName);
        if (helloAck.sounds) this.setStoreValue('sounds', helloAck.sounds).catch(this.error);
        this.setAvailable().catch(this.error);
      },
      onDisconnect: () => {
        this.log('Disconnected');
        this._onDisconnect();
      },
    });

    this.registerCapabilityListener('speaker_playing', async (value) => {
      if (value) {
        await this.playSound('test', 100);
      } else {
        await this._client.send({ type: MSG.STOP_ALL });
      }
    });

    await this._client.connect().catch((err) => {
      this.log('Initial connect failed:', err.message);
      this.setUnavailable(err.message).catch(this.error);
    });
  }

  _createClient(opts) {
    return new WebSocketClient(opts);
  }

  async onDeleted() {
    if (this._client) await this._client.disconnect();
  }

  async playSound(soundId, volume, { loop = false } = {}) {
    const effectiveVolume = volume ?? (this.getStore().globalVolume ?? 100);
    const handle = randomUUID();
    const msg = { type: MSG.PLAY, soundId, volume: effectiveVolume, handle };
    if (loop) msg.loop = true;
    await this._client.send(msg);
    return handle;
  }

  async stopSound(handle) {
    await this._client.send({ type: MSG.STOP, handle });
  }

  async stopAll() {
    await this._client.send({ type: MSG.STOP_ALL });
  }

  async setGlobalVolume(volume) {
    await this.setStoreValue('globalVolume', volume);
  }

  isAnySoundPlaying() {
    return this._handles.getAll().length > 0;
  }

  isSoundPlaying(soundName) {
    return this._handles.isPlaying(soundName);
  }

  _handleMessage(msg) {
    switch (msg.type) {
      case MSG.STARTED:
        this._handles.add(msg.handle, {
          soundId: msg.soundId,
          soundName: msg.soundName,
          startedAt: Date.now(),
          durationMs: msg.durationMs,
        });
        this._updateSpeakerPlaying();
        this.homey.flow.getDeviceTriggerCard('sound_started')
          .trigger(this, { sound_name: msg.soundName, handle: msg.handle, duration_ms: msg.durationMs })
          .catch(this.error);
        break;

      case MSG.DONE:
        this._handles.remove(msg.handle);
        this._updateSpeakerPlaying();
        this.homey.flow.getDeviceTriggerCard('sound_done')
          .trigger(this, { sound_name: msg.soundName, handle: msg.handle, reason: msg.reason })
          .catch(this.error);
        break;

      case MSG.LIBRARY_UPDATE:
        this.setStoreValue('sounds', msg.sounds).catch(this.error);
        break;

      case MSG.HELLO_ACK:
        if (msg.sounds) this.setStoreValue('sounds', msg.sounds).catch(this.error);
        break;
    }
  }

  _updateSpeakerPlaying() {
    const playing = this._handles.getAll().length > 0;
    this.setCapabilityValue('speaker_playing', playing).catch(this.error);
  }

  _onDisconnect() {
    const removed = this._handles.clear();
    this.setCapabilityValue('speaker_playing', false).catch(this.error);
    this.setUnavailable('Connection lost').catch(this.error);
    for (const entry of removed) {
      this.homey.flow.getDeviceTriggerCard('sound_done')
        .trigger(this, { sound_name: entry.soundName, handle: entry.handle, reason: 'connection_lost' })
        .catch(this.error);
    }
  }

}

module.exports = SoundboardDevice;
