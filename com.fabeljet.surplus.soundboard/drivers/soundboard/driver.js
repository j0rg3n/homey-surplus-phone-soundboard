'use strict';

const { Driver } = require('homey');
const WebSocketClient = require('../../lib/WebSocketClient');
const { DEFAULT_PORT } = require('../../lib/constants');

class SoundboardDriver extends Driver {

  async onInit() {
    // --- play_sound action ---
    const playCard = this.homey.flow.getActionCard('play_sound');

    playCard.registerRunListener(async (args) => {
      const handle = await args.device.playSound(args.sound.id, args.volume ?? 100);
      return { handle };
    });

    playCard.getArgument('sound').registerAutocompleteListener(async (query, args) => {
      const sounds = args.device.getStore().sounds ?? [];
      const available = args.device.getAvailable();
      const suffix = available ? '' : ' (offline)';
      return sounds
        .filter(s => !query || s.name.toLowerCase().includes(query.toLowerCase()))
        .map(s => ({ id: s.id, name: s.name + suffix }));
    });

    // --- stop_sound action ---
    this.homey.flow.getActionCard('stop_sound')
      .registerRunListener(async (args) => {
        await args.device.stopSound(args.handle);
      });

    // --- stop_all action ---
    this.homey.flow.getActionCard('stop_all')
      .registerRunListener(async (args) => {
        await args.device.stopAll();
      });

    // --- set_volume action ---
    this.homey.flow.getActionCard('set_volume')
      .registerRunListener(async (args) => {
        await args.device.setGlobalVolume(args.volume);
      });

    // --- is_playing condition ---
    this.homey.flow.getConditionCard('is_playing')
      .registerRunListener(async (args) => {
        return args.device.isAnySoundPlaying();
      });

    // --- sound_is_playing condition ---
    const soundIsPlayingCard = this.homey.flow.getConditionCard('sound_is_playing');

    soundIsPlayingCard.registerRunListener(async (args) => {
      return args.device.isSoundPlaying(args.sound.name);
    });

    soundIsPlayingCard.getArgument('sound').registerAutocompleteListener(async (query, args) => {
      const sounds = args.device.getStore().sounds ?? [];
      return sounds
        .filter(s => !query || s.name.toLowerCase().includes(query.toLowerCase()))
        .map(s => ({ id: s.id, name: s.name }));
    });

    // --- trigger run listeners (always pass) ---
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
