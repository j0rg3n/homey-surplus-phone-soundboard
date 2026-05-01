'use strict';

const { Driver } = require('homey');
const WebSocketClient = require('../../lib/WebSocketClient');
const { DEFAULT_PORT } = require('../../lib/constants');

class SoundboardDriver extends Driver {

  async onInit() {
    const playCard = this.homey.flow.getActionCard('play_sound');

    playCard.registerRunListener(async (args) => {
      await args.device.playSound(args.sound.id, args.volume ?? 100);
    });

    playCard.getArgument('sound').registerAutocompleteListener(async (query, args) => {
      const sounds = args.device.getStore().sounds ?? [];
      const available = args.device.getAvailable();
      const suffix = available ? '' : ' (offline)';
      return sounds
        .filter(s => !query || s.name.toLowerCase().includes(query.toLowerCase()))
        .map(s => ({ id: s.id, name: s.name + suffix }));
    });

    this.homey.flow.getDeviceTriggerCard('sound_started')
      .registerRunListener(async () => true);

    this.homey.flow.getDeviceTriggerCard('sound_done')
      .registerRunListener(async () => true);
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
