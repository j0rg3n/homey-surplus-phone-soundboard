'use strict';

const { Driver } = require('homey');
const WebSocketClient = require('../../lib/WebSocketClient');
const { DEFAULT_PORT } = require('../../lib/constants');

class SoundboardDriver extends Driver {

  async onInit() {
    this.homey.flow.getActionCard('play_sound').registerRunListener(async (args) => {
      await args.device.playSound(args.sound_id, args.volume ?? 100);
    });
  }

  async onPair(session) {
    session.setHandler('connect', async ({ ip, port }) => {
      const client = new WebSocketClient({
        ip,
        port: Number(port) || DEFAULT_PORT,
        onMessage: () => {},
        onConnect: () => {},
        onDisconnect: () => {},
      });

      const helloAck = await client.connect();
      await client.disconnect();
      return { deviceName: helloAck.deviceName };
    });
  }

}

module.exports = SoundboardDriver;
