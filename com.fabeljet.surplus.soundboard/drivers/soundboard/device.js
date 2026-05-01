'use strict';

const { Device } = require('homey');
const { randomUUID } = require('crypto');
const WebSocketClient = require('../../lib/WebSocketClient');
const { MSG, PROTOCOL_VERSION } = require('../../lib/constants');

class SoundboardDevice extends Device {

  async onInit() {
    const { ip, port } = this.getStore();
    this.log(`Initialising device — ${ip}:${port}`);

    this._client = new WebSocketClient({
      ip,
      port,
      onMessage: (msg) => this._handleMessage(msg),
      onConnect: (helloAck) => {
        this.log('Connected, device:', helloAck.deviceName);
        this.setAvailable().catch(this.error);
      },
      onDisconnect: () => {
        this.log('Disconnected');
        this.setUnavailable('Connection lost').catch(this.error);
      },
    });

    await this._client.connect().catch((err) => {
      this.log('Initial connect failed:', err.message);
      this.setUnavailable(err.message).catch(this.error);
    });
  }

  async onDeleted() {
    if (this._client) {
      await this._client.disconnect();
    }
  }

  async playSound(soundId, volume) {
    await this._client.send({
      type: MSG.PLAY,
      soundId,
      volume,
      handle: randomUUID(),
    });
  }

  _handleMessage(msg) {
    this.log('Received:', msg.type);
  }

}

module.exports = SoundboardDevice;
